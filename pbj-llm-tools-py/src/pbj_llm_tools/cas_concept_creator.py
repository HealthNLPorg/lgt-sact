import logging
from typing import Dict, List

from cassis import Cas

logger = logging.getLogger(__name__)


class CasConceptCreator:

    def __init__(self):
        self.type_fs_array = None

    def init_types(self, cas: Cas):
        """The best way to initialize types used by this class is with a Cas. Only done once."""
        if self.type_fs_array is not None:
            return
        self.type_fs_array = cas.typesystem.get_type("uima.cas.FSArray")

    def create_concept(self, cas: Cas, concept_type, text: str, mentions: List):
        """
        Create a Concept.  A Concept is always created, with Mentions being assigned if possible.

        Args:
             cas: ye olde ...
             concept_type: concept type to add to cas
             text: concept text
             mentions: dict of texts, possibly in document text, to mentions

        Returns:
            A concept Type with its text representation set to the text,
            containing any Mention types that could be identified in the cas text.
        """
        self.init_types(cas)
        concept = concept_type()
        concept.textRepresentation = text
        # mentions in an element are held in a cas FSArray type.
        concept.mentions = self.type_fs_array(elements=mentions)
        cas.add(concept)
        return concept

    def create_concepts(self, cas: Cas, concept_type,
                        text_mentions: Dict[str, List]) -> Dict[str, object]:
        """
        Create Concepts.  Concepts are always created, with Mentions being assigned if possible.

        Args:
             cas: ye olde ...
             concept_type: concept type to add to cas
             text_mentions: dict of texts, possibly in document text, to mentions

        Returns:
            A dictionary of text to a concept Type with its text representation set to the text,
            containing any Mention types that could be identified in the cas text.
        """
        concepts = {}
        for text, mentions in text_mentions:
            concepts[text] = self.create_concept(cas, concept_type, text, mentions)
        return concepts
