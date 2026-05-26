from langgraph.graph import StateGraph, END

from pbj_langgraph.pbj_send_node import PBJSendNode

from lg_timelines.lgt_constants import *
from lg_timelines.lgt_state import LGTState
from lg_timelines.lgt_llm_node import LGTLLMNode
from lg_timelines.tlinks_to_cas import TlinkCasImporter
from lg_timelines.normalize_time_node import NormalizeTimeNode

NODE_FIND_TLINKS = 'find_tlinks'
NODE_FILL_CAS = 'fill_cas'
NODE_NORMALIZE_TIME = 'normalize_time'
NODE_WRITE_OUTPUT = 'write_output'
NODE_STORE_CAS = 'store_cas'

OPTION_FILL_CAS = 'fill_cas'
OPTION_WRITE_OUTPUT = 'write_output'
OPTION_END_GRAPH = 'end_graph'

# We have a choice after finding tlinks NODE_FIND_TLINKS.
# One edge goes through post-processing of tlinks, the other skips to the end of the graph.
fill_cas_or_write = {
    OPTION_FILL_CAS: NODE_FILL_CAS,
    OPTION_WRITE_OUTPUT: NODE_WRITE_OUTPUT
}


def has_tlinks(state: LGTState):
    for section_tlinks in state["tlinks_by_section"]:
        if len(section_tlinks) != 0:
            for tlink_dict in section_tlinks:
                if len(tlink_dict) != 0:
                    return OPTION_FILL_CAS
    return OPTION_WRITE_OUTPUT


class LGTGraph:
    def __init__(self):
        self.graph = None
        self.normalize_times_node = None
        self.write_output_node = None
        self.final_cas = None

    def create_graph(self,
                     normalize_time_queue: str = QUEUE_NORMALIZE_TIME,
                     time_normalized_queue: str = QUEUE_TIME_NORMALIZED,
                     write_output_queue: str = QUEUE_WRITE_OUTPUT,
                     tlinks_adapter: str = MODEL_PATH_CHEMOTIME,
                     base_llm_name: str = BASE_LLM_NAME,
                     hf_token: str = HUGGINGFACE_TOKEN,
                     use_cpu: bool = USE_CPU,
                     tlinks_batch_size: int = BATCH_SIZE_TLINKS):
        """Create a graph that has finds medications, times, and links between them in text,
         normalizes the times, and calls for output.

         Args:
             normalize_time_queue: a queue that will accept a Cas for time normalization.
             time_normalized_queue: a queue to listen to for normalized times.
             write_output_queue: a queue to send the cas for writing output.
             tlinks_adapter: the path to our trained model.
             base_llm_name: name/path of our desired LLM.
             hf_token: HuggingFace token for access to our trained model.
             tlinks_batch_size: How many messages we should put in a batch call to the model.
             use_cpu: If there is no GPU then this must be set to True or LGT will not find tlinks.
         """

        # Clean State -> Get Doc -> Ping Reader -> Find TLinks -> Put TLinks in Cas -> Normalize Times -> Write Output
        #                                        -> (or Skip TLinks processing)     -                  ->
        #                                                       ->  ( or Skip TLink Post-Processing )  ->
        graph = StateGraph(LGTState)
        # Creates the tlinks.
        find_tlinks_node = LGTLLMNode(adapter_path=tlinks_adapter,
                                      base_llm_name=base_llm_name,
                                      hf_token=hf_token,
                                      use_cpu=use_cpu,
                                      batch_size=tlinks_batch_size)
        # Puts tlinks into cas.
        fill_cas_node = TlinkCasImporter()
        # Sends the cas to a time normalization queue and receives the returned cas.
        self.normalize_times_node = NormalizeTimeNode(normalize_time_queue=normalize_time_queue,
                                                      time_normalized_queue=time_normalized_queue)
        # Sends the cas to an output writer.
        self.write_output_node = PBJSendNode(target_queue=write_output_queue)

        graph.add_node(NODE_FIND_TLINKS, find_tlinks_node)
        graph.add_node(NODE_FILL_CAS, fill_cas_node)
        graph.add_node(NODE_NORMALIZE_TIME, self.normalize_times_node)
        graph.add_node(NODE_WRITE_OUTPUT, self.write_output_node)
        graph.add_node(NODE_STORE_CAS, self.store_cas_node)

        graph.set_entry_point(NODE_FIND_TLINKS)
        graph.add_conditional_edges(NODE_FIND_TLINKS, has_tlinks, fill_cas_or_write)
        graph.add_edge(NODE_FILL_CAS, NODE_NORMALIZE_TIME)
        graph.add_edge(NODE_NORMALIZE_TIME, NODE_WRITE_OUTPUT)
        graph.add_edge(NODE_WRITE_OUTPUT, NODE_STORE_CAS)
        graph.add_edge(NODE_STORE_CAS, END)
        self.graph = graph.compile()

    def run_graph(self, state: LGTState):
        # Once the END is hit, the actual state of state and its members is inconsistent.
        # We MUST use an internal cas value to ensure that what we have after the graph executes is the
        # true final cas.
        # self.final_cas = state["cas"]
        self.graph.invoke(state, config={"recursion_limit": 1000})
        return self.final_cas

    def send_stops(self):
        self.normalize_times_node.send_stop()
        self.write_output_node.send_stop()

    def handle_exception(self):
        self.normalize_times_node.handle_exception()
        self.write_output_node.handle_exception()

    def store_cas_node(self, state: LGTState):
        self.final_cas = state["cas"]
        return {}

