import logging

from ctakes_pbj.pbj_tools.pbj_defaults import *
from pbj_langgraph.pbj_receive_node import PBJReceiveNode
from pbj_langgraph.pbj_send_node import PBJSendNode
from lg_timelines.lgt_state import LGTState
from lg_timelines.lgt_constants import QUEUE_NORMALIZE_TIME, QUEUE_TIME_NORMALIZED

logger = logging.getLogger(__name__)


class NormalizeTimeNode(PBJReceiveNode):

    def __init__(self, normalize_time_queue: str = QUEUE_NORMALIZE_TIME,
                 time_normalized_queue: str = QUEUE_TIME_NORMALIZED,
                 host_name=DEFAULT_HOST, port_name=DEFAULT_PORT,
                 username=DEFAULT_USER, password=DEFAULT_PASS):
        super().__init__(time_normalized_queue, host_name, port_name, username, password)
        self.send_node = PBJSendNode(target_queue=normalize_time_queue,
                                     host_name=host_name, port_name=port_name,
                                     username=username, password=password)

    def __call__(self, state: LGTState):
        self.send_node.__call__(state)
        # via superclass PBJReceiveNode, Listen to normalized queue and return received cas.
        # if not state["processing_complete"]:
        return super().__call__(LGTState)

    # Called when an exception is thrown.
    def handle_exception(self):
        self.send_node.handle_exception()
        super().handle_exception()

    def send_stop(self):
        self.send_node.send_stop()
        