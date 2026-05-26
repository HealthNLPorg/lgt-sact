from typing import TypedDict
from cassis import Cas


class CASState(TypedDict):
    # cTAKES CAS to hold transferable between-process data.
    cas: Cas

    # set to true when a pbj input queue has received a stop signal.  Indicates that we are done.
    processing_complete: bool
