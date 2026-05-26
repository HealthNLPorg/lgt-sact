import logging
from typing import List, Dict

import os
import psutil

from huggingface_hub import login
from transformers import AutoModelForCausalLM, AutoTokenizer, AutoConfig, BitsAndBytesConfig
from peft import PeftModel
import torch

from ctakes_pbj.type_system.ctakes_types import Segment
from pbj_llm_tools.llm_io_formatting import list_str_to_list
from lg_timelines.lgt_state import LGTState
from lg_timelines.lgt_constants import *

torch.manual_seed(MODEL_SEED)
if torch.cuda.is_available():
    torch.cuda.manual_seed_all(MODEL_SEED)

logger = logging.getLogger(__name__)


def create_message(prompt: str, section_text: str) -> List[Dict[str, str]]:
    """
    Create a message to send the llm based upon an actionable prompt and document text.
    :param prompt: for the llm
    :param section_text: text in some part of the document
    :return: a list of instruction messages
    """
    instruction = prompt + section_text + "/no_think"
    return [
        {"role": "user", "content": instruction}
    ]


def create_message_batches(state: LGTState, batch_size: int) -> List[List[Dict[str, str]]]:
    """
    Split the messages into batches.  Supposedly done for speed.
    :param state: graph state containing current CAS and prompt
    :param batch_size:
    :return:
    """
    cas = state["cas"]
    prompt = state["tlinks_prompt"]
    messages = []
    for section in cas.select(Segment):
        message = create_message(prompt, section.get_covered_text())
        messages.append(message)
    return [messages[i:i + batch_size] for i in range(0, len(messages), batch_size)]


def get_free_memory():
    if torch.cuda.is_available():
        free, total = torch.cuda.mem_get_info()
        return free / 1024 ** 3
    mem = psutil.virtual_memory()
    return mem.available / 1024 ** 3


def print_mem_info():
    if torch.cuda.is_available():
        print_gpu_info()
    else:
        print_cpu_info()


def print_cpu_info():
    logger.info(f"Torch will attempt to use the CPU with the following free / total memory (GB):")
    mem = psutil.virtual_memory()
    print(f"{mem.available / 1024 ** 3:.2f} / {mem.total / 1024 ** 3:.2f}")


def print_gpu_info():
    logger.info(f"Torch CUDA is available with the following GPUs and free / total memory (GB):")
    for i in range(torch.cuda.device_count()):
        free, total = torch.cuda.mem_get_info(i)
        print(f"{i}:  {free / 1024 ** 3:.2f} / {total / 1024 ** 3:.2f}")


quantization_4bit = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.bfloat16,
    bnb_4bit_use_double_quant=False,
)

quantization_8bit = BitsAndBytesConfig(
    load_in_8bit=True,
)


class LGTLLMNode:
    def __init__(self,
                 adapter_path: str = MODEL_PATH_CHEMOTIME,
                 base_llm_name: str = BASE_LLM_NAME,
                 hf_token: str = HUGGINGFACE_TOKEN,
                 use_cpu: bool = USE_CPU,
                 batch_size: int = BATCH_SIZE_TLINKS):
        self.tokenizer = None
        self.model = None
        self.use_cpu = use_cpu
        self.batch_size = batch_size
        print_mem_info()
        if torch.cuda.is_available() or use_cpu == 'yes':
            self.initialize_model(adapter_path, base_llm_name, hf_token, batch_size)
        else:
            logger.info("Torch CUDA is not available and use_cpu is not yes, not initializing LGT LLM Node.")

    def initialize_model_large(self,
                               adapter_path: str = MODEL_PATH_CHEMOTIME,
                               base_llm_name: str = BASE_LLM_NAME):
        logger.info("Initializing LGT LLM Node using 16bit floating point precision ...")
        base_model = AutoModelForCausalLM.from_pretrained(
            base_llm_name,
            torch_dtype=torch.bfloat16,
            device_map="auto",
            offload_folder="offload/",
            trust_remote_code=True)
        self.model = PeftModel.from_pretrained(base_model,
                                               adapter_path,
                                               torch_dtype=torch.bfloat16,
                                               device_map="auto",
                                               offload_folder="offload/",
                                               trust_remote_code=True)

    def initialize_model_small(self, quantization_config, bit_size: int,
                               adapter_path: str = MODEL_PATH_CHEMOTIME,
                               base_llm_name: str = BASE_LLM_NAME):
        logger.info(f"Initializing LGT LLM Node using {bit_size}bit quantization ...")
        base_model = AutoModelForCausalLM.from_pretrained(
            base_llm_name,
            quantization_config=quantization_config,
            device_map="auto",
            offload_folder="offload/",
            trust_remote_code=True)
        self.model = PeftModel.from_pretrained(base_model,
                                               adapter_path,
                                               quantization_config=quantization_config,
                                               device_map="auto",
                                               offload_folder="offload/",
                                               trust_remote_code=True)

    def initialize_model(self,
                         adapter_path: str = MODEL_PATH_CHEMOTIME,
                         base_llm_name: str = BASE_LLM_NAME,
                         hf_token: str = HUGGINGFACE_TOKEN,
                         batch_size: int = BATCH_SIZE_TLINKS):
        if not hf_token:
            logger.info("No HuggingFace Token specified. If you aren't already authorized then this app will crash.")
        else:
            login(token=hf_token)
            os.environ["HF_TOKEN"] = hf_token
        logger.info("Please note that if this is your first time running, the model needs to be downloaded.")
        logger.info("The model is roughly 30GB in size, so the download can take an extremely long time.")
        logger.info(f"Using (consistency) model seed: {MODEL_SEED}")
        try:
            config = AutoConfig.from_pretrained(base_llm_name, trust_remote_code=True)
            tokenizer = AutoTokenizer.from_pretrained(pretrained_model_name_or_path=adapter_path,
                                                      config=config,
                                                      trust_remote_code=True,
                                                      token=hf_token)
            tokenizer.padding_side = 'left'
            # Enable padding for batch processing
            if tokenizer.pad_token is None:
                tokenizer.pad_token = tokenizer.eos_token
            self.tokenizer = tokenizer
        except Exception as exception_1:
            logger.error(f"Could not initialize tokenizer from {adapter_path}", exception_1)
            raise exception_1
        try:
            # free, total = torch.cuda.mem_get_info()
            free = get_free_memory()
            if free < 18:
                self.initialize_model_small(quantization_4bit, 4, adapter_path, base_llm_name)
            elif free < 32:
                self.initialize_model_small(quantization_8bit, 8, adapter_path, base_llm_name)
            else:
                self.initialize_model_large(adapter_path, base_llm_name)
            logger.info(f"Model memory size: {self.model.get_memory_footprint()}")
            print_mem_info()
        except Exception as exception_2:
            print_mem_info()
            logger.error(f"Could not initialize model from {base_llm_name}", exception_2)
            raise exception_2
        self.model.eval()
        self.batch_size = batch_size
        # logger.info(f"Using Model {adapter_path} and {base_llm_name} with a batch size of {batch_size}.")

    def __call__(self, state: LGTState):
        message_batches = create_message_batches(state, self.batch_size)
        # message_batches = List[List[Dict[str, str]]]
        tlinks_by_section = []
        # tlinks_by_section = List[List[Dict[str,str]]]
        # List of TLinks per batch entry (section)
        for message_batch in message_batches:
            # message_batch = List[Dict[str, str]]
            formatted_chats = self.create_formatted_chats(message_batch)
            tokenized_inputs = self.tokenizer(formatted_chats, padding=True, return_tensors="pt").to(self.model.device)
            # logger.info(f"{len(tokenized_inputs)} tokenized_inputs:")
            # print(tokenized_inputs)
            batch_entry_llm_outputs = self.get_batch_llm_outputs(tokenized_inputs)
            # logger.info(f"{len(batch_entry_llm_outputs)} batch_entry_llm_outputs:")
            # print(batch_entry_llm_outputs)
            batch_entry_tlinks = self.llm_out_to_tlinks(tokenized_inputs, batch_entry_llm_outputs)
            # batch_entry_tlinks = List[List[Dict[str, str]]]
            # logger.info(f"{len(batch_entry_tlinks)} batch_entry_tlinks:")
            # print(batch_entry_tlinks)
            tlinks_by_section.extend(batch_entry_tlinks)
            # tlinks_by_section = List[List[Dict[str,str]]]
        return {
            # [
            #  ['<think>\n\n</think>\n\n
            #   [\n
            #    {\n    "SACT": "chemotherapy",\n    "relation": "BEGINS-ON",\n    "time": "July 10, 2023"\n  },\n
            #    {\n    "SACT": "Doxorubicin",\n    "relation": "ENDS-ON",\n    "time": "September 18, 2023"\n  },\n
            #    {\n    "SACT": "Cyclophosphamide",\n  "relation": "ENDS-ON",\n    "time": "September 18, 2023"\n  },\n
            #    {\n    "SACT": "Paclitaxel",\n    "relation": "BEGINS-ON",\n    "time": "October 2, 2023"\n  },\n
            #    {\n    "SACT": "Taxol",\n    "relation": "CONTAINS-1",\n    "time": "yesterday"\n  },\n
            #    {\n    "SACT": "Anastrozole",\n    "relation": "BEGINS-ON",\n    "time": "October 15, 2023"\n  },\n
            #    {\n    "SACT": "Paclitaxel",\n    "relation": "ENDS-ON",\n    "time": "November 27, 2023"\n  }\n
            #   ]
            # ']
            # ]
            # tlinks_by_section = List[List[Dict[str,str]]]
            "tlinks_by_section": tlinks_by_section
        }

    def create_formatted_chats(self, message_batch: List[Dict[str, str]]) -> List:
        formatted_chats = []
        for message in message_batch:
            formatted_chat = self.tokenizer.apply_chat_template(
                message,
                tokenize=False,
                add_generation_prompt=True
            )
            formatted_chats.append(formatted_chat)
        return formatted_chats

    def get_batch_llm_outputs(self, tokenized_inputs) -> List[str]:
        logger.info("Generating LLM batch outputs ...")
        with torch.inference_mode():
            batch_llm_outputs = self.model.generate(
                input_ids=tokenized_inputs.input_ids,
                attention_mask=tokenized_inputs.attention_mask,
                max_new_tokens=16000,
                pad_token_id=self.tokenizer.pad_token_id,
                eos_token_id=self.tokenizer.eos_token_id,
            )
        return batch_llm_outputs

    def llm_out_to_tlinks(self, tokenized_inputs, batch_entry_llm_outputs: List[str]) -> List[List[Dict[str, str]]]:
        batch_entry_tlinks = []
        for j in range(len(batch_entry_llm_outputs)):
            input_length = tokenized_inputs.attention_mask[j].sum().item()
            first_tlink_token = tokenized_inputs.input_ids.shape[-1] - input_length
            tlink_tokens_no_padding = batch_entry_llm_outputs[j][first_tlink_token:]
            generated_tokens_no_input_prompt = tlink_tokens_no_padding[input_length:]
            decoded_tokens = self.tokenizer.decode(generated_tokens_no_input_prompt, skip_special_tokens=True)
            # decoded_tokens is a str, where str contains header/footer and a list of dictionary
            # list_of_dict = dict_list_str_to_dict_list(decoded_tokens)
            list_of_dict = list_str_to_list(decoded_tokens)
            # logger.info(f"{len(list_of_dict)} list_of_dict: {list_of_dict}")
            # batch_entry_tlinks.append(decoded_tokens)
            # batch_entry_tlinks is a list of str, where str contains header/footer and a list of dictionary
            batch_entry_tlinks.append(list_of_dict)
            # logger.info(f"{len(batch_entry_tlinks)} batch_entry_tlinks: {batch_entry_tlinks}")
        return batch_entry_tlinks
