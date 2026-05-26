import logging
import torch

from ctakes_pbj.component.cas_annotator import *

from lg_timelines.lgt_constants import *
from lg_timelines.lgt_graph import LGTGraph

logger = logging.getLogger(__name__)


class LGTGraphAE(CasAnnotator):

    def __init__(self):
        self.lgt_graph = None
        self.initial_state = None
        self.tlinks_prompt_path = None
        self.normalize_time_queue = None
        self.time_normalized_queue = None
        self.write_output_queue = None
        self.tlinks_adapter = None
        self.base_llm_name = None
        self.hf_token = None
        self.tlinks_batch_size = None
        self.use_cpu = False
        self.can_run = False

    def declare_params(self, arg_parser):
        arg_parser.add_arg('--tlinks_prompt_path', default=PROMPT_PATH_TLINKS)
        arg_parser.add_arg('--normalize_time_queue', default=QUEUE_NORMALIZE_TIME)
        arg_parser.add_arg('--time_normalized_queue', default=QUEUE_TIME_NORMALIZED)
        arg_parser.add_arg('-wq', '--write_output_queue', default=QUEUE_WRITE_OUTPUT)
        arg_parser.add_arg('--tlinks_adapter', default=MODEL_PATH_CHEMOTIME)
        arg_parser.add_arg('--base_llm_name', default=BASE_LLM_NAME)
        arg_parser.add_arg('-hf', '--hf_token', type=str, default=HUGGINGFACE_TOKEN)
        arg_parser.add_arg('--tlinks_batch_size', type=int, default=BATCH_SIZE_TLINKS)
        arg_parser.add_arg('--use_cpu', type=str, default=USE_CPU)

    def init_params(self, args):
        self.tlinks_prompt_path = args.tlinks_prompt_path
        self.normalize_time_queue = args.normalize_time_queue
        self.time_normalized_queue = args.time_normalized_queue
        self.write_output_queue = args.write_output_queue
        self.tlinks_adapter = args.tlinks_adapter
        self.base_llm_name = args.base_llm_name
        self.hf_token = args.hf_token
        self.tlinks_batch_size = args.tlinks_batch_size
        self.use_cpu = args.use_cpu
        self.can_run = torch.cuda.is_available() or args.use_cpu == 'yes'

    def initialize(self):
        if not self.can_run:
            logger.info("LGT Graph will not run as there are no GPUs and --use-cpu is not set to yes.")
            return
        # logger.info(f"Current Directory: {os.getcwd()}")
        with open(self.tlinks_prompt_path, 'r') as f:
            tlinks_prompt = f.read()
        self.lgt_graph = LGTGraph()
        self.lgt_graph.create_graph(normalize_time_queue=self.normalize_time_queue,
                                    time_normalized_queue=self.time_normalized_queue,
                                    write_output_queue=self.write_output_queue,
                                    tlinks_adapter=self.tlinks_adapter,
                                    base_llm_name=self.base_llm_name,
                                    hf_token=self.hf_token,
                                    use_cpu=self.use_cpu,
                                    tlinks_batch_size=self.tlinks_batch_size)
        self.initial_state = {
            "tlinks_prompt": tlinks_prompt,
            "tlinks_by_section": [],
            "processing_complete": False
        }

    def process(self, cas):
        if not self.can_run:
            return
        state = self.initial_state
        state["cas"] = cas
        cas = self.lgt_graph.run_graph(state)
        return cas

    def collection_process_complete(self):
        if self.can_run:
            self.send_stops()

    # Called when an exception is thrown.
    def handle_exception(self, thrower, exceptable, initializing=False):
        if self.can_run:
            self.lgt_graph.handle_exception()

    def send_stops(self):
        if self.can_run:
            self.lgt_graph.send_stops()
