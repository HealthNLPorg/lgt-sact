import logging
import difflib
import unicodedata
import re
from dataclasses import dataclass
from enum import Enum
from typing import List, Optional, Tuple, NamedTuple

logger = logging.getLogger(__name__)

# From  https://shanechang.com/p/
# demystifying-text-anchoring-langextract/#building-your-own-a-dependency-free-text-alignment-algorithm


class MatchStatus(Enum):
    """Status of quote matching attempt."""
    EXACT = "match_exact"
    FUZZY = "match_fuzzy"
    NOT_FOUND = "not_found"


@dataclass
class SpanMatch:
    """Result of matching a quote against source text."""
    quote: str
    start: Optional[int]
    end: Optional[int]
    score: float
    status: MatchStatus


class MatchResult(NamedTuple):
    """Internal result from matching operations."""
    start: Optional[int]
    end: Optional[int]
    score: float


class TextNormalizer:
    """Handles text normalization for consistent matching."""

    @staticmethod
    def normalize(text: str) -> str:
        """Normalize whitespace and unicode for robust matching."""
        if not text:
            return ""

        # normalization of whitespace may have ruined offsets.
        # Unicode normalization
        # text = unicodedata.normalize("NFC", text)
        # Collapse whitespace
        # text = re.sub(r"\s+", " ", text).strip()
        # Could perhaps substitute space " " for \r and \n characters.
        return text

    @staticmethod
    def create_index_map(original: str, normalized: str) -> List[int]:
        """Create mapping from normalized indices back to original indices."""
        # For production use - maps each normalized char position to original position
        # This is a simplified version; full implementation would handle complex cases
        index_map = []
        orig_idx = 0

        for norm_char in normalized:
            # Find next matching character in original
            while orig_idx < len(original) and original[orig_idx].isspace() and norm_char != ' ':
                orig_idx += 1
            if orig_idx < len(original):
                index_map.append(orig_idx)
                orig_idx += 1
            else:
                index_map.append(len(original))

        return index_map


class MatchingConfig:
    """Configuration for matching behavior."""

    def __init__(
            self,
            threshold: float = 0.85,
            exact_threshold: float = 0.999,
            min_step_size: int = 8,
            step_fraction: int = 4,
            window_padding: int = 64
    ):
        self.threshold = threshold
        self.exact_threshold = exact_threshold
        self.min_step_size = min_step_size
        self.step_fraction = step_fraction
        self.window_padding = window_padding

    def get_step_size(self, quote_length: int) -> int:
        """Calculate step size for sliding window."""
        return max(self.min_step_size, quote_length // self.step_fraction)

    def get_window_size(self, quote_length: int) -> int:
        """Calculate window size for fuzzy matching."""
        return quote_length + self.window_padding


class ExactMatcher:
    """Handles exact string matching."""

    @staticmethod
    def find_exact_match(text: str, quote: str) -> MatchResult:
        """Find exact match of quote in text."""
        start = text.find(quote)
        if start != -1:
            return MatchResult(start, start + len(quote), 1.0)
        return MatchResult(None, None, 0.0)


class FuzzyMatcher:
    """Handles fuzzy string matching using difflib."""

    def __init__(self, config: MatchingConfig):
        self.config = config

    def find_fuzzy_match(self, text: str, quote: str) -> MatchResult:
        """Find best fuzzy match using sliding window approach."""
        if not quote or not text:
            return MatchResult(None, None, 0.0)

        best_match = MatchResult(None, None, 0.0)
        quote_len = len(quote)

        step_size = self.config.get_step_size(quote_len)
        window_size = self.config.get_window_size(quote_len)

        # Slide window across text
        for i in range(0, max(1, len(text) - quote_len + 1), step_size):
            window = text[i:i + window_size]
            match_result = self._match_in_window(quote, window, i)

            if match_result.score > best_match.score:
                best_match = match_result

        # Only return match if it meets threshold
        if best_match.score >= self.config.threshold:
            return best_match

        return MatchResult(None, None, best_match.score)

    def _match_in_window(self, quote: str, window: str, window_start: int) -> MatchResult:
        """Match quote against a specific window of text."""
        matcher = difflib.SequenceMatcher(a=quote, b=window, autojunk=False)
        score = matcher.ratio()

        if score <= 0:
            return MatchResult(None, None, score)

        # Find best matching block to estimate position
        blocks = matcher.get_matching_blocks()
        if not blocks:
            return MatchResult(None, None, score)

        # Use largest matching block (excluding sentinel)
        main_block = max(blocks[:-1], key=lambda b: b.size, default=blocks[0])

        # Calculate match position in original text
        match_start = window_start + main_block.b - main_block.a
        match_start = max(0, match_start)
        match_end = match_start + len(quote)

        return MatchResult(match_start, match_end, score)


class QuoteAligner:
    """Main class for aligning quotes with source text."""

    def __init__(self, config: Optional[MatchingConfig] = None):
        self.config = config or MatchingConfig()
        self.normalizer = TextNormalizer()
        self.exact_matcher = ExactMatcher()
        self.fuzzy_matcher = FuzzyMatcher(self.config)

    def align_quote(self, source_text: str, quote: str) -> SpanMatch:
        """Align a single quote with source text."""
        if not quote or not source_text:
            return SpanMatch(quote, None, None, 0.0, MatchStatus.NOT_FOUND)

        # Normalize both texts
        norm_source = self.normalizer.normalize(source_text)
        norm_quote = self.normalizer.normalize(quote)

        # Try exact match first
        exact_result = self.exact_matcher.find_exact_match(norm_source, norm_quote)
        if exact_result.start is not None:
            return SpanMatch(
                quote=quote,
                start=exact_result.start,
                end=exact_result.end,
                score=exact_result.score,
                status=MatchStatus.EXACT
            )

        # Fall back to fuzzy matching
        fuzzy_result = self.fuzzy_matcher.find_fuzzy_match(norm_source, norm_quote)
        if fuzzy_result.start is not None:
            status = (MatchStatus.EXACT if fuzzy_result.score >= self.config.exact_threshold
                      else MatchStatus.FUZZY)
            return SpanMatch(
                quote=quote,
                start=fuzzy_result.start,
                end=fuzzy_result.end,
                score=fuzzy_result.score,
                status=status
            )

        # No match found
        return SpanMatch(
            quote=quote,
            start=None,
            end=None,
            score=fuzzy_result.score,
            status=MatchStatus.NOT_FOUND
        )

    def align_quotes(self, source_text: str, quotes: List[str]) -> List[SpanMatch]:
        """Align multiple quotes with source text."""
        return [self.align_quote(source_text, quote) for quote in quotes]


# Convenience function for simple usage
def align_quotes(source_text: str, quotes: List[str], threshold: float = 0.85) -> List[SpanMatch]:
    """
    Simple interface for aligning quotes with source text.

    Args:
        source_text: The source document text
        quotes: List of quotes to find in the source
        threshold: Minimum similarity score for fuzzy matches (0.0-1.0)

    Returns:
        List of SpanMatch objects with alignment results
    """
    config = MatchingConfig(threshold=threshold)
    aligner = QuoteAligner(config)
    return aligner.align_quotes(source_text, quotes)


# Example usage
if __name__ == "__main__":
    # Example with exact match
    source = "Nintendo can set the price unchallenged in their market segment."
    quotes = [
        "Nintendo can set the price unchallenged",  # Exact match
        "Nintendo can set prices without competition",  # Fuzzy match
        "Sony dominates the market"  # No match
    ]

    results = align_quotes(source, quotes, threshold=0.8)

    for result in results:
        print(f"Quote: '{result.quote}'")
        print(f"Status: {result.status.value}")
        print(f"Score: {result.score:.3f}")
        if result.start is not None:
            print(f"Position: [{result.start}:{result.end}]")
            print(f"Found: '{source[result.start:result.end]}'")
        print("-" * 50)


