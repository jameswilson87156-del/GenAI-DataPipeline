"""
Python 清洗算力节点：FastAPI 微服务

启动方式：
    pip install fastapi "uvicorn[standard]"
    uvicorn clean_worker:app --host 0.0.0.0 --port 8000

接口：
    POST /api/v1/clean

请求示例：
{
  "item_id": 1001,
  "data_type": "TEXT",
  "raw_content": "<p>Hello</p>  world"
}

返回示例：
{
  "item_id": 1001,
  "data_type": "TEXT",
  "cleaned_content": "Hello world"
}
"""

from __future__ import annotations

import html
import re
import unicodedata
from enum import Enum

from fastapi import FastAPI
from pydantic import BaseModel, Field


class DataType(str, Enum):
    """清洗数据类型。"""

    TEXT = "TEXT"
    CODE = "CODE"


class CleanRequest(BaseModel):
    """Java 主控发给 Python worker 的清洗请求。"""

    item_id: int = Field(..., description="数据项 ID")
    data_type: DataType = Field(..., description="数据类型：TEXT 或 CODE")
    raw_content: str = Field(..., description="待清洗原始文本")


class CleanResponse(BaseModel):
    """Python worker 返回给 Java 主控的清洗结果。"""

    item_id: int = Field(..., description="数据项 ID")
    data_type: DataType = Field(..., description="数据类型：TEXT 或 CODE")
    cleaned_content: str = Field(..., description="清洗后的文本")


app = FastAPI(
    title="GenAI Clean Worker",
    version="1.0.0",
    description="负责接收 Java 主控派发的清洗任务，并执行文本/代码预处理。",
)


@app.post("/api/v1/clean", response_model=CleanResponse, summary="执行清洗任务")
def clean(request: CleanRequest) -> CleanResponse:
    """
    统一清洗入口。

    - TEXT：去 HTML 标签、特殊乱码符号、多余空白。
    - CODE：去冗余空行、统一缩进和尾部空格，做基础格式预处理。
    """
    if request.data_type == DataType.TEXT:
        cleaned_content = clean_text(request.raw_content)
    else:
        cleaned_content = clean_code(request.raw_content)

    return CleanResponse(
        item_id=request.item_id,
        data_type=request.data_type,
        cleaned_content=cleaned_content,
    )


def clean_text(raw_content: str) -> str:
    """
    文本文本清洗逻辑：
    1. 先做 HTML entity 反转义，例如 &nbsp; -> 空格。
    2. 用正则移除 HTML 标签。
    3. 移除常见乱码与不可见控制字符。
    4. 压缩重复空白，保留基础可读性。
    """
    content = html.unescape(raw_content or "")
    content = re.sub(r"<[^>]+>", " ", content)
    content = strip_invalid_chars(content)

    # 去掉常见的替换字符、零宽字符、BOM 等乱码痕迹。
    content = re.sub(r"[\u200b-\u200f\u202a-\u202e\ufeff\ufffd]+", " ", content)

    # 将连续空白折叠为一个空格，保留最终自然阅读体验。
    content = re.sub(r"\s+", " ", content).strip()
    return content


def clean_code(raw_content: str) -> str:
    """
    代码清洗逻辑：
    1. 统一换行符和缩进风格。
    2. 去除每行尾部空格。
    3. 去掉冗余连续空行，最多保留一个空行。
    4. 做少量无副作用的基础格式规范化，作为后续 AST 解析前置处理。
    """
    content = raw_content or ""
    content = content.replace("\r\n", "\n").replace("\r", "\n")
    content = content.replace("\t", "    ")

    normalized_lines: list[str] = []
    blank_line_count = 0

    for line in content.split("\n"):
        # 只去尾部空格，不碰左侧缩进，避免破坏 Python/YAML 等语义。
        stripped_line = line.rstrip()
        stripped_line = normalize_code_line(stripped_line)

        if stripped_line == "":
            blank_line_count += 1
            if blank_line_count > 1:
                continue
        else:
            blank_line_count = 0

        normalized_lines.append(stripped_line)

    cleaned = "\n".join(normalized_lines).strip()
    return cleaned


def normalize_code_line(line: str) -> str:
    """
    对单行代码做轻量级美化：
    - 逗号后补一个空格；
    - 去掉行尾分号前多余空格；
    - 收紧大括号/小括号前后的明显噪声空格；
    这些规则都是低风险预处理，不尝试做真正 AST 级格式化。
    """
    if not line:
        return line

    line = re.sub(r",(?=\S)", ", ", line)
    line = re.sub(r"\s+([;,\)\]\}])", r"\1", line)
    line = re.sub(r"([\(\[\{])\s+", r"\1", line)
    line = re.sub(r"\s+\{", " {", line)
    return line


def strip_invalid_chars(content: str) -> str:
    """
    去除控制字符、私有区字符、代理项等异常符号。

    - 保留换行、制表符、普通可打印字符；
    - 将非法字符替换为空格，避免不同单词直接黏连。
    """
    cleaned_chars: list[str] = []
    for char in content:
        if char in {"\n", "\r", "\t"}:
            cleaned_chars.append(char)
            continue

        category = unicodedata.category(char)
        if category.startswith("C"):
            cleaned_chars.append(" ")
            continue

        cleaned_chars.append(char)
    return "".join(cleaned_chars)
