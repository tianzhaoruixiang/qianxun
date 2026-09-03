"""
千寻本地 Mem0 OSS REST 入口。

基于官方 mem0/mem0-api-server 镜像替换 main.py：
- 去掉强制 Neo4j（仅 pgvector）
- LLM / Embedder 可走 LiteLLM（OPENAI_BASE_URL）
- 无鉴权（仅内网 compose；勿对公网暴露）
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field

from mem0 import Memory

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
load_dotenv()

POSTGRES_HOST = os.environ.get("POSTGRES_HOST", "mem0-postgres")
POSTGRES_PORT = int(os.environ.get("POSTGRES_PORT", "5432"))
POSTGRES_DB = os.environ.get("POSTGRES_DB", "postgres")
POSTGRES_USER = os.environ.get("POSTGRES_USER", "postgres")
POSTGRES_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "postgres")
POSTGRES_COLLECTION_NAME = os.environ.get("POSTGRES_COLLECTION_NAME", "memories")
EMBEDDING_DIMS = int(os.environ.get("MEM0_EMBEDDING_DIMS", os.environ.get("EMBEDDING_MODEL_DIMS", "1024")))

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY") or os.environ.get("MEM0_OPENAI_API_KEY")
OPENAI_BASE_URL = (
    os.environ.get("OPENAI_BASE_URL")
    or os.environ.get("OPENAI_API_BASE")
    or os.environ.get("MEM0_OPENAI_BASE_URL")
    or ""
).rstrip("/")
HISTORY_DB_PATH = os.environ.get("HISTORY_DB_PATH", "/app/history/history.db")
LLM_MODEL = os.environ.get("MEM0_DEFAULT_LLM_MODEL", os.environ.get("MEM0_LLM_MODEL", "openai-default"))
EMBEDDER_MODEL = os.environ.get(
    "MEM0_DEFAULT_EMBEDDER_MODEL",
    os.environ.get("MEM0_EMBEDDER_MODEL", "text-embedding-v3"),
)

if not OPENAI_API_KEY:
    raise RuntimeError("OPENAI_API_KEY（或 MEM0_OPENAI_API_KEY）未设置；Mem0 需要 LLM/Embedder 密钥")


def _openai_block(model: str, temperature: float | None = None) -> Dict[str, Any]:
    cfg: Dict[str, Any] = {"api_key": OPENAI_API_KEY, "model": model}
    if OPENAI_BASE_URL:
        # 旧版 mem0 Embedder 认 openai_base_url；勿传 base_url
        cfg["openai_base_url"] = OPENAI_BASE_URL
    if temperature is not None:
        cfg["temperature"] = temperature
    return cfg


DEFAULT_CONFIG: Dict[str, Any] = {
    "version": "v1.1",
    "vector_store": {
        "provider": "pgvector",
        "config": {
            "host": POSTGRES_HOST,
            "port": POSTGRES_PORT,
            "dbname": POSTGRES_DB,
            "user": POSTGRES_USER,
            "password": POSTGRES_PASSWORD,
            "collection_name": POSTGRES_COLLECTION_NAME,
            "embedding_model_dims": EMBEDDING_DIMS,
        },
    },
    "llm": {"provider": "openai", "config": _openai_block(LLM_MODEL, temperature=0.2)},
    "embedder": {"provider": "openai", "config": _openai_block(EMBEDDER_MODEL)},
    "history_db_path": HISTORY_DB_PATH,
}

MEMORY_INSTANCE = Memory.from_config(DEFAULT_CONFIG)

# 运行时可被 /configure/embedder 覆盖（供系统设置热更新）
_RUNTIME_EMBEDDER_MODEL = EMBEDDER_MODEL
_RUNTIME_EMBEDDING_DIMS = EMBEDDING_DIMS
_RUNTIME_EMBEDDER_BASE_URL = OPENAI_BASE_URL

app = FastAPI(
    title="Mem0 REST APIs (qianxun local)",
    description="千寻本地 Mem0 OSS：仅 pgvector，无 Neo4j。",
    version="1.0.0-qianxun",
)


class Message(BaseModel):
    role: str = Field(..., description="Role of the message (user or assistant).")
    content: str = Field(..., description="Message content.")


class MemoryCreate(BaseModel):
    messages: List[Message] = Field(..., description="List of messages to store.")
    user_id: Optional[str] = None
    agent_id: Optional[str] = None
    run_id: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None


class SearchRequest(BaseModel):
    query: str = Field(..., description="Search query.")
    user_id: Optional[str] = None
    run_id: Optional[str] = None
    agent_id: Optional[str] = None
    filters: Optional[Dict[str, Any]] = None
    top_k: Optional[int] = Field(None, description="Maximum number of results.")


class EmbedderConfigureRequest(BaseModel):
    model: str = Field(..., description="Embedding model id, e.g. text-embedding-v3")
    embedding_dims: int = Field(..., ge=64, le=8192, description="Vector dimensions")
    openai_base_url: Optional[str] = Field(None, description="OpenAI Compatible base URL for embeddings")
    openai_api_key: Optional[str] = Field(None, description="API key for embeddings / LLM")
    llm_model: Optional[str] = Field(None, description="Optional LLM model for fact extraction")
    collection_name: Optional[str] = Field(None, description="Optional pgvector collection override")


def _public_config() -> Dict[str, Any]:
    return {
        "ok": True,
        "service": "mem0",
        "vector_store": "pgvector",
        "embedder_model": _RUNTIME_EMBEDDER_MODEL,
        "embedding_dims": _RUNTIME_EMBEDDING_DIMS,
        "embedder_base_url": _RUNTIME_EMBEDDER_BASE_URL,
        "llm_model": (DEFAULT_CONFIG.get("llm") or {}).get("config", {}).get("model"),
        "collection_name": (DEFAULT_CONFIG.get("vector_store") or {}).get("config", {}).get("collection_name"),
    }


def _rebuild_memory(
    *,
    embedder_model: str,
    embedding_dims: int,
    openai_base_url: str,
    openai_api_key: str,
    llm_model: str,
    collection_name: str,
) -> None:
    global MEMORY_INSTANCE, DEFAULT_CONFIG
    global _RUNTIME_EMBEDDER_MODEL, _RUNTIME_EMBEDDING_DIMS, _RUNTIME_EMBEDDER_BASE_URL
    global OPENAI_API_KEY, OPENAI_BASE_URL

    OPENAI_API_KEY = openai_api_key
    OPENAI_BASE_URL = (openai_base_url or "").rstrip("/")
    _RUNTIME_EMBEDDER_MODEL = embedder_model
    _RUNTIME_EMBEDDING_DIMS = embedding_dims
    _RUNTIME_EMBEDDER_BASE_URL = OPENAI_BASE_URL

    DEFAULT_CONFIG = {
        "version": "v1.1",
        "vector_store": {
            "provider": "pgvector",
            "config": {
                "host": POSTGRES_HOST,
                "port": POSTGRES_PORT,
                "dbname": POSTGRES_DB,
                "user": POSTGRES_USER,
                "password": POSTGRES_PASSWORD,
                "collection_name": collection_name,
                "embedding_model_dims": embedding_dims,
            },
        },
        "llm": {"provider": "openai", "config": _openai_block(llm_model, temperature=0.2)},
        "embedder": {"provider": "openai", "config": _openai_block(embedder_model)},
        "history_db_path": HISTORY_DB_PATH,
    }
    MEMORY_INSTANCE = Memory.from_config(DEFAULT_CONFIG)


@app.get("/health")
def health():
    return _public_config()


@app.get("/configure", summary="Get current Mem0 configuration (redacted)")
def get_config():
    return _public_config()


@app.post("/configure", summary="Configure Mem0")
def set_config(config: Dict[str, Any]):
    global MEMORY_INSTANCE, DEFAULT_CONFIG
    global _RUNTIME_EMBEDDER_MODEL, _RUNTIME_EMBEDDING_DIMS, _RUNTIME_EMBEDDER_BASE_URL
    MEMORY_INSTANCE = Memory.from_config(config)
    DEFAULT_CONFIG = config
    emb = ((config.get("embedder") or {}).get("config") or {})
    vec = ((config.get("vector_store") or {}).get("config") or {})
    _RUNTIME_EMBEDDER_MODEL = str(emb.get("model") or _RUNTIME_EMBEDDER_MODEL)
    _RUNTIME_EMBEDDING_DIMS = int(vec.get("embedding_model_dims") or _RUNTIME_EMBEDDING_DIMS)
    _RUNTIME_EMBEDDER_BASE_URL = str(emb.get("openai_base_url") or _RUNTIME_EMBEDDER_BASE_URL)
    return {"message": "Configuration set successfully", **_public_config()}


@app.post("/configure/embedder", summary="Hot-update embedder model and dims")
def configure_embedder(body: EmbedderConfigureRequest):
    model = body.model.strip()
    if not model:
        raise HTTPException(status_code=400, detail="model 不能为空")
    dims = int(body.embedding_dims)
    api_key = (body.openai_api_key or OPENAI_API_KEY or "").strip()
    if not api_key:
        raise HTTPException(status_code=400, detail="缺少 openai_api_key")
    base_url = (body.openai_base_url or OPENAI_BASE_URL or "").strip().rstrip("/")
    llm_model = (body.llm_model or LLM_MODEL or "openai-default").strip()
    # 维数变化时换 collection，避免 pgvector 维度冲突；旧记忆不自动迁移
    collection = (body.collection_name or "").strip()
    if not collection:
        base_collection = POSTGRES_COLLECTION_NAME or "memories"
        collection = f"{base_collection}_d{dims}"
    try:
        _rebuild_memory(
            embedder_model=model,
            embedding_dims=dims,
            openai_base_url=base_url,
            openai_api_key=api_key,
            llm_model=llm_model,
            collection_name=collection,
        )
    except Exception as e:
        logging.exception("configure_embedder failed:")
        raise HTTPException(status_code=500, detail=str(e)) from e
    return {"message": "Embedder updated", **_public_config()}


@app.post("/memories", summary="Create memories")
def add_memory(memory_create: MemoryCreate):
    if not any([memory_create.user_id, memory_create.agent_id, memory_create.run_id]):
        raise HTTPException(status_code=400, detail="At least one identifier (user_id, agent_id, run_id) is required.")
    params = {k: v for k, v in memory_create.model_dump().items() if v is not None and k != "messages"}
    try:
        response = MEMORY_INSTANCE.add(messages=[m.model_dump() for m in memory_create.messages], **params)
        return JSONResponse(content=response)
    except Exception as e:
        logging.exception("Error in add_memory:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.get("/memories", summary="Get memories")
def get_all_memories(
    user_id: Optional[str] = None,
    run_id: Optional[str] = None,
    agent_id: Optional[str] = None,
):
    if not any([user_id, run_id, agent_id]):
        raise HTTPException(status_code=400, detail="At least one identifier is required.")
    try:
        params = {k: v for k, v in {"user_id": user_id, "run_id": run_id, "agent_id": agent_id}.items() if v is not None}
        return MEMORY_INSTANCE.get_all(**params)
    except Exception as e:
        logging.exception("Error in get_all_memories:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.get("/memories/{memory_id}", summary="Get a memory")
def get_memory(memory_id: str):
    try:
        return MEMORY_INSTANCE.get(memory_id)
    except Exception as e:
        logging.exception("Error in get_memory:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/search", summary="Search memories")
def search_memories(search_req: SearchRequest):
    try:
        params: Dict[str, Any] = {
            k: v for k, v in search_req.model_dump().items() if v is not None and k not in {"query", "top_k"}
        }
        # 兼容 Platform 风格：filters 内的实体 ID 提升到顶层（旧 OSS Memory.search 更稳）
        filters = dict(params.get("filters") or {})
        for key in ("user_id", "agent_id", "run_id"):
            if key in filters and key not in params:
                params[key] = filters.pop(key)
        # OSS 不保证支持 app_id 过滤，避免误伤召回
        filters.pop("app_id", None)
        if filters:
            params["filters"] = filters
        elif "filters" in params:
            del params["filters"]
        if search_req.top_k is not None:
            # 旧版 OSS Memory.search 用 limit，不认 top_k
            params["limit"] = search_req.top_k
        return MEMORY_INSTANCE.search(query=search_req.query, **params)
    except Exception as e:
        logging.exception("Error in search_memories:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.put("/memories/{memory_id}", summary="Update a memory")
def update_memory(memory_id: str, updated_memory: Dict[str, Any]):
    try:
        return MEMORY_INSTANCE.update(memory_id=memory_id, data=updated_memory.get("text") or updated_memory.get("data"))
    except Exception as e:
        logging.exception("Error in update_memory:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.get("/memories/{memory_id}/history", summary="Get memory history")
def memory_history(memory_id: str):
    try:
        return MEMORY_INSTANCE.history(memory_id=memory_id)
    except Exception as e:
        logging.exception("Error in memory_history:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.delete("/memories/{memory_id}", summary="Delete a memory")
def delete_memory(memory_id: str):
    try:
        MEMORY_INSTANCE.delete(memory_id=memory_id)
        return {"message": "Memory deleted successfully"}
    except Exception as e:
        logging.exception("Error in delete_memory:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.delete("/memories", summary="Delete all memories")
def delete_all_memories(
    user_id: Optional[str] = None,
    run_id: Optional[str] = None,
    agent_id: Optional[str] = None,
):
    if not any([user_id, run_id, agent_id]):
        raise HTTPException(status_code=400, detail="At least one identifier is required.")
    try:
        params = {k: v for k, v in {"user_id": user_id, "run_id": run_id, "agent_id": agent_id}.items() if v is not None}
        MEMORY_INSTANCE.delete_all(**params)
        return {"message": "All relevant memories deleted"}
    except Exception as e:
        logging.exception("Error in delete_all_memories:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.post("/reset", summary="Reset all memories")
def reset_memory():
    try:
        MEMORY_INSTANCE.reset()
        return {"message": "All memories reset"}
    except Exception as e:
        logging.exception("Error in reset_memory:")
        raise HTTPException(status_code=500, detail=str(e)) from e


@app.get("/", include_in_schema=False)
def home():
    return RedirectResponse(url="/docs")
