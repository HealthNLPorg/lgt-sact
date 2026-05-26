import logging
from threading import Event

from langgraph.graph import StateGraph

from stomp import Connection12, ConnectionListener
from ctakes_pbj.pbj_tools import helper_functions
from ctakes_pbj.pbj_tools.pbj_defaults import *
from ctakes_pbj.type_system.type_system_loader import *
from pbj_langgraph.cas_state import CASState

logger = logging.getLogger(__name__)


class PBJGraphStarter(ConnectionListener):

    def __init__(self, queue_name, host_name=DEFAULT_HOST, port_name=DEFAULT_PORT, username=DEFAULT_USER,
                 password=DEFAULT_PASS, r_id: str = None, listener_name: str = None):
        self.cas = None
        self.source_queue = queue_name
        self.source_host = host_name
        self.source_port = port_name
        self.password = password
        self.username = username
        self.typesystem = None
        self.conn = None
        self.message_receivable = Event()
        self.message_received = Event()
        self.receiver_started = False
        self.receiver_stopped = False
        self.graph = None
        if listener_name is None:
            self.listener_name = "Receive_" + self.source_queue
        else:
            self.listener_name = listener_name
        if r_id is None:
            self.r_id = abs(hash(self.listener_name))
        else:
            self.r_id = r_id

    def set_graph(self, graph: StateGraph):
        self.graph = graph

    def get_document(self, state: CASState):
        self.start_listening()
        # Start a second thread to allow the receiver to reconnect when necessary as it waits for a message.
        while not self.message_received.is_set():
            self.message_received.wait()
        if self.receiver_stopped:
            # return {"processing_complete": True}
            state["processing_complete"] = True
        else:
            # return {"cas": self.cas}
            state["cas"] = self.cas
        try:
            self.graph.invoke(state, config={"recursion_limit": 1000})
        except Exception as app_exception:
            logger.info(f"Exception thrown: {type(app_exception).__name__}.")
            self.handle_exception()
            raise
        return state

    def on_message(self, frame):
        while not self.message_receivable.is_set():
            self.message_receivable.wait()
        self.stop_listening()
        if frame.body == STOP_MESSAGE:
            logger.info(f"{self.listener_name} received Stop Code from {self.source_host} {self.source_queue}.")
            self.stop_receiver()
        else:
            if XMI_INDICATOR in frame.body:
                self.cas = cassis.load_cas_from_xmi(frame.body, self.get_typesystem())
                # ack is not supported in artemis.  Neither are stomp Transactions.
                # self.conn.ack(frame.headers['message-id'], self.listener_name)
                logger.info(f"{self.listener_name} received CAS for {helper_functions.get_document_id(self.cas)} from"
                            f" {self.source_host} {self.source_queue}")
                self.message_received.set()
            else:
                logger.error(f"{self.listener_name} received a malformed message: {frame.body}")
                self.start_listening()

    def on_disconnected(self):
        if not self.receiver_stopped:
            # logger.warning("Disconnected, reconnecting ...")
            self.connect_and_subscribe()

    def on_error(self, frame):
        logger.error(f"{self.listener_name} Receiver Error: {frame.body}")

    def start_receiver(self):
        logger.info(f"{self.listener_name} starting Receiver on {self.source_host} {self.source_queue} ...")
        # Use a heartbeat of 10 minutes  (in milliseconds)
        self.conn = Connection12([(self.source_host, self.source_port)],
                                 keepalive=True, heartbeats=(600000, 600000))
        self.conn.set_listener(self.listener_name, self)
        self.connect_and_subscribe()

    def connect_and_subscribe(self):
        self.conn.connect(self.username, self.password, wait=True)
        self.conn.subscribe(destination=self.source_queue, id=self.r_id, ack='auto')

    def start_listening(self):
        if not self.receiver_started:
            self.start_receiver()
            self.receiver_started = True
        self.message_received.clear()
        self.message_receivable.set()

    def stop_listening(self):
        self.message_receivable.clear()

    def stop_receiver(self):
        self.stop_listening()
        self.receiver_stopped = True
        self.conn.unsubscribe(destination=self.source_queue, id=self.r_id)
        self.conn.disconnect()
        logger.info(f"{self.listener_name} Receiver disconnected from  {self.source_host} {self.source_queue}.")
        self.message_received.set()

    def set_typesystem(self, typesystem):
        self.typesystem = typesystem

    def get_typesystem(self):
        if self.typesystem is None:
            type_system_accessor = TypeSystemLoader()
            type_system_accessor.load_type_system()
            self.set_typesystem(type_system_accessor.get_type_system())
        return self.typesystem

    def handle_exception(self):
        self.stop_receiver()
