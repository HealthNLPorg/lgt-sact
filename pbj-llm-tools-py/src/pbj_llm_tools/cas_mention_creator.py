import logging
from typing import Dict, List

from cassis import Cas

from ctakes_pbj.pbj_tools.create_type import add_type as pbj_add_type
from pbj_llm_tools.text_offset_finder import MatchingConfig, SpanMatch, MatchStatus, QuoteAligner

logger = logging.getLogger(__name__)


class CasMentionCreator:
    """
    Can move text to mentions in the Cas.
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
        config = MatchingConfig(threshold=threshold)
        self.aligner = QuoteAligner(config)

    def create_all_fragment_mentions(self, cas: Cas, mention_type,
                                     fragment_text: str, fragment_begin: int,
                                     texts: set) -> Dict[str, List]:
        """
        Create a Concept.  A Concept is always created, with Mentions being assigned if possible.

        Args:
             cas: ye olde ...
             mention_type: mention type to add to cas
             fragment_text: text in searchable section
             fragment_begin: begin character offset of searchable section
             texts: set of texts, possibly in document text

        Returns:
            A dictionary of text to any Mention types that could be identified in the cas text.
        """
        all_mentions = {}
        for text in texts:
            all_mentions[text] = self.create_fragment_mentions(cas, mention_type, fragment_text, fragment_begin, text)
        return all_mentions

    def create_fragment_mentions(self, cas: Cas, mention_type,
                                 fragment_text: str, fragment_begin: int,
                                 text: str) -> List:
        """
        Create Mentions, if possible.

        Args:
             cas: ye olde ...
             mention_type: mention type to add to cas
             fragment_text: text in searchable section
             fragment_begin: begin character offset of searchable section
             text: text, possibly in document text

        Returns:
            A list of any Mention types that could be identified in the cas text.
        """
        mentions = []
        if text is None:
            return mentions
        span_matches = self.get_span_matches(fragment_text, text)
        for span_match in span_matches:
            if span_match.status == MatchStatus.NOT_FOUND:
                break
            mention = pbj_add_type(cas, mention_type,
                                   fragment_begin + span_match.start, fragment_begin + span_match.end)
            mention.confidence = span_match.score
            mentions.append(mention)
        return mentions

    def get_span_matches(self, section_text: str, to_match: str) -> List[SpanMatch]:
        # TODO - docs
        span_match = self.get_span_match(section_text, 0, to_match)
        if span_match.status == MatchStatus.NOT_FOUND:
            return [span_match]
        span_matches = []
        while span_match.status != MatchStatus.NOT_FOUND:
            span_matches.append(span_match)
            if span_match.end >= len(section_text):
                return span_matches
            span_match = self.get_span_match(section_text, span_match.end, to_match)
        return span_matches

    def get_span_match(self, all_text: str, begin_offset: int, to_match: str) -> SpanMatch:
        span_match = self.aligner.align_quote(all_text[begin_offset:], to_match)
        if span_match.status != MatchStatus.NOT_FOUND:
            span_match.start += begin_offset
            span_match.end += begin_offset
        return span_match
