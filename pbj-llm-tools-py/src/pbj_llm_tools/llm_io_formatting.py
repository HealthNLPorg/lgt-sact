import ast
import logging
from typing import Dict, List

logger = logging.getLogger(__name__)


# Because of possible headers and footers, and possible whitespace, ast.literal_eval may not work appropriately.


def de_think(llm_out: str) -> str:
    # e.g.
    # '<think>\n\n</think>\n\n[\n  {\n    "SACT": "chemo",\n    "relation": "BEGINS-ON",\n    "time": "2023"\n  }\n]'
    close_think = llm_out.find("</think>")
    if close_think < 0:
        logger.warning(f"No think closing tag in LLM output {llm_out}")
        return llm_out
    return llm_out[close_think + 8:].strip()


def list_str_to_list(llm_out: str) -> List:
    # e.g.
    # '<think>\n\n</think>\n\n[\n  "thing1",\n    "thing2",\n    "thing3"\n  \n]'
    left_bracket = llm_out.find("[")
    if left_bracket < 0:
        logger.error(f"No List opening bracket in LLM output {llm_out}")
        return [llm_out]
    right_bracket = llm_out.rfind("]")
    if right_bracket < 0:
        logger.error(f"No List closing bracket in LLM output {llm_out}")
        return [llm_out[left_bracket:]]
    try:
        return ast.literal_eval(llm_out[left_bracket:right_bracket+1])
    except (ValueError, SyntaxError) as e:
        logger.error(f"Invalid List format in LLM output {llm_out}")
        return [llm_out[left_bracket:right_bracket+1]]
    # for item in a series of strings from splitting the contents string by comma, strip whitespace. Placed in list.
    # return [item.strip() for item in contents.split(",")]


def dict_str_to_dict(llm_out: str) -> Dict[str, object]:
    # e.g.
    # '<think>\n\n</think>\n\n{\n    "SACT": "chemo",\n    "relation": "BEGINS-ON",\n    "time": "2023"\n  }\n'
    left_brace = llm_out.find("{")
    if left_brace < 0:
        logger.warning(f"No Dictionary opening brace in LLM output {llm_out}")
        return {"llm_out": llm_out}
    right_brace = llm_out.rfind("}")
    if right_brace < 0:
        logger.warning(f"No Dictionary closing brace in LLM output {llm_out}")
        return {"llm_out": llm_out}
    try:
        return ast.literal_eval(llm_out[left_brace:right_brace+1])
    except (ValueError, SyntaxError) as e:
        logger.error(f"Invalid Dictionary format in LLM output {llm_out}")
        return {"llm_output": llm_out}
    # contents_dict = {}
    # for item in contents.split(","):
    #     split = item.strip()
    #     name_value = split.split(":")
    #     if len(name_value) != 2:
    #         logger.error(f"Illegal name : value pairing in {name_value}")
    #         continue
    #     contents_dict[name_value[0].strip()] = name_value[1].strip()
    # return contents_dict


def dict_list_str_to_dict_list(llm_out: str) -> List[Dict[str, object]]:
    # e.g.
    # '<think>\n\n</think>\n\n[\n  {\n    "SACT": "chemo",\n    "relation": "BEGINS-ON",\n    "time": "2023"\n  }\n]'
    dict_list = []
    for content in list_str_to_list(llm_out):
        dict_list.append(dict_str_to_dict(content))
    return dict_list
