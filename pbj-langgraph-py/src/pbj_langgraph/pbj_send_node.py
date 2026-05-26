import logging
from stomp import Connection12

from ctakes_pbj.pbj_tools.pbj_defaults import *
from pbj_langgraph.cas_state import CASState

logger = logging.getLogger(__name__)


class PBJSendNode:

    def __init__(self, target_queue: str,
                 host_name=DEFAULT_HOST, port_name=DEFAULT_PORT,
                 username=DEFAULT_USER, password=DEFAULT_PASS,
                 sender_name: str = None):
        self.target_queue = target_queue
        self.target_host = host_name
        self.target_port = port_name
        self.password = password
        self.username = username
        if sender_name is None:
            self.sender_name = "Send_" + self.target_queue
        else:
            self.sender_name = sender_name
        self.send_conn = None
        logger.info(f"{self.sender_name} starting Sender on {self.target_host} {self.target_queue} ...")
        # Use a heartbeat of 10 minutes  (in milliseconds)
        self.send_conn = Connection12([(self.target_host, self.target_port)],
                                      keepalive=True, heartbeats=(600000, 600000))
        self.send_conn.connect(self.username, self.password, wait=True)

    def __call__(self, state: CASState):
        processing_complete = state["processing_complete"]
        if processing_complete:
            self.send_stop()
        else:
            logger.info(f"{self.sender_name} sending to {self.target_host} {self.target_queue} ...")
            xmi = state["cas"].to_xmi()
            # Send the CAS to normalization queue
            self.send_conn.send(self.target_queue, xmi)
            # via superclass PBJReceiveNode, Listen to normalized queue and return received cas.
        return {}

    def send_stop(self):
        if self.send_conn is None:
            return
        logger.info(f"{self.sender_name} sending Stop Code to {self.target_host} {self.target_queue} ...")
        self.send_conn.send(self.target_queue, STOP_MESSAGE)
        self.send_conn.disconnect()
        logger.info(f"{self.sender_name} Sender disconnected from  {self.target_host} {self.target_queue}.")

    # Called when an exception is thrown.
    def handle_exception(self):
        self.send_stop()
