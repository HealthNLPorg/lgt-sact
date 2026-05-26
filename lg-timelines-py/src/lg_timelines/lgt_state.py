from typing import Dict, List

from pbj_langgraph.cas_state import CASState


class LGTState(CASState):
    # Never clear this.
    tlinks_prompt: str

    # [
    #   { "SACT": "chemotherapy", "relation": "BEGINS-ON", "time": "July 10, 2023" }
    #   { "SACT": "Doxorubicin", "relation": "ENDS-ON", "time": "September 18, 2023" }
    # ]
    tlinks_by_section: List[List[Dict[str, str]]]
