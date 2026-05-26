import logging

from cassis import Cas

from ctakes_pbj.type_system.ctakes_types import (Segment,
                                                 Medication, MedicationMention,
                                                 Time, TimeMention,
                                                 TemporalRelation)
from pbj_llm_tools.cas_concept_creator import CasConceptCreator
from pbj_llm_tools.cas_mention_creator import CasMentionCreator
from lg_timelines.lgt_state import LGTState
from lg_timelines.lgt_constants import UNKNOWN_SACT, UNKNOWN_TIME, UNKNOWN_RELATION

logger = logging.getLogger(__name__)


class TlinkCasImporter:
    """
    Can move data from the model's List[List[Dict(str, str)]] data to the Cas.
    Uses the currently favored paradigm (a la DeepPhe) of the Concept being the first-class citizen and holding
    references to text Mentions.
    Subclasses of the cTAKES type Element are used to represent Concepts.
    Subclasses of IdentifiedAnnotation are used for text Mentions.
    Likewise, Concept Relations are of primary import, while Mention Relations are secondary.
    Concept Relations use subclasses of the Relation type, Mention relations use subclasses of BinaryTextRelation.
    For various reasons, LLMs are terrible at returning text offsets, so we attempt
    to match the text of returned data with offsets in the document using some 3rd party code:
    https://shanechang.com/p/
    demystifying-text-anchoring-langextract/#building-your-own-a-dependency-free-text-alignment-algorithm
    """

    def __init__(self, threshold: float = 0.85):
        self.type_medication = None
        self.type_medication_mention = None
        self.type_time = None
        self.type_time_mention = None
        self.type_time_relation = None
        # self.time_text_relation_type = None
        self.cas_mention_creator = CasMentionCreator()
        self.cas_concept_creator = CasConceptCreator()

    def __call__(self, state: LGTState):
        self.add_tlinks_to_cas(state)

    def init_types(self, cas: Cas):
        """The best way to initialize types used by this class is with a Cas. Only done once."""
        if self.type_medication is not None:
            return
        self.type_medication = cas.typesystem.get_type(Medication)
        self.type_medication_mention = cas.typesystem.get_type(MedicationMention)
        self.type_time = cas.typesystem.get_type(Time)
        self.type_time_mention = cas.typesystem.get_type(TimeMention)
        self.type_time_relation = cas.typesystem.get_type(TemporalRelation)
        # self.time_text_relation_type = cas.typesystem.get_type(TemporalTextRelation)

    def add_tlinks_to_cas(self, state: LGTState):
        """Entry point for adding tlink information found by LLM to the Cas."""
        tlinks_by_section = state["tlinks_by_section"]
        #  tlinks_by_section = List[List[Dict[str, str]]]
        # List of TLinks per batch entry (section)
        # logger.info(f"{len(tlinks_by_section)} tlinks_by_section:")
        # print(tlinks_by_section)
        if len(tlinks_by_section) == 0:
            return
        cas = state["cas"]
        self.init_types(cas)
        sections = cas.select(Segment)
        if len(sections) != len(tlinks_by_section):
            logger.error(f"Section count not equal to tlink set count {len(sections)} "
                         f"vs. {len(tlinks_by_section)}.")
            return
        # Create dicts of med text to mention list, time text to mention list.
        all_med_mentions = {}
        all_time_mentions = {}
        for i in range(len(sections)):
            section_tlinks = tlinks_by_section[i]
            # section_tlinks = List[Dict[str, str]
            logger.info(f"Number of TLinks in Section {i}: {len(section_tlinks)}")
            fragment_text = sections[i].get_covered_text()
            fragment_begin = sections[i].begin
            for tlink in section_tlinks:
                # tlink = Dict[str, str]
                # logger.info("TLink:")
                # print(tlink)
                med_mentions = self.cas_mention_creator.create_fragment_mentions(cas,
                                                                                 self.type_medication_mention,
                                                                                 fragment_text,
                                                                                 fragment_begin,
                                                                                 tlink.get("SACT", UNKNOWN_SACT))
                all_med_mentions.setdefault(tlink.get("SACT", UNKNOWN_SACT), []).extend(med_mentions)
                time_mentions = self.cas_mention_creator.create_fragment_mentions(cas,
                                                                                  self.type_time_mention,
                                                                                  fragment_text,
                                                                                  fragment_begin,
                                                                                  tlink.get("time", UNKNOWN_TIME))
                all_time_mentions.setdefault(tlink.get("time", UNKNOWN_TIME), []).extend(time_mentions)
        all_med_concepts = {}
        all_time_concepts = {}
        # logger.info(f"{len(all_med_mentions)} all_med_mentions:")
        # print(all_med_mentions)
        for text, mentions in all_med_mentions.items():
            all_med_concepts[text] = self.cas_concept_creator.create_concept(cas, self.type_medication, text, mentions)
        # logger.info(f"{len(all_time_mentions)} all_time_mentions:")
        # print(all_time_mentions)
        for text, mentions in all_time_mentions.items():
            all_time_concepts[text] = self.cas_concept_creator.create_concept(cas, self.type_time, text, mentions)
        for i in range(len(sections)):
            section_tlinks = tlinks_by_section[i]
            for tlink in section_tlinks:
                # tlink = Dict[str, str]
                relation = self.type_time_relation()
                relation.category = tlink.get("relation", UNKNOWN_RELATION)
                relation.arg1 = all_med_concepts.get(tlink.get("SACT", UNKNOWN_SACT))
                relation.arg2 = all_time_concepts.get(tlink.get("time", UNKNOWN_TIME))
                cas.add(relation)
