"""Merge all system turns into a single leading system message.

Qwen3.5/3.6/3.8 chat templates reject any system message that is not
messages[0]. Claude Agent SDK may send a top-level system plus mid-conversation
system turns; this hook coalesces them before the OpenAI-compat upstream.
"""

from __future__ import annotations

import os
from typing import Any, Mapping

try:
    from litellm.integrations.custom_logger import CustomLogger
except ImportError:  # local checks without the proxy image

    class CustomLogger:  # type: ignore[no-redef]
        pass


def _env_enabled(name: str, default: bool = True) -> bool:
    raw = os.getenv(name)
    if raw is None or not str(raw).strip():
        return default
    return str(raw).strip().lower() in ("1", "true", "yes", "on")


def _content_to_text(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            text = _content_to_text(block)
            if text:
                parts.append(text)
        return "\n".join(parts)
    if isinstance(content, dict):
        if "text" in content:
            return _content_to_text(content.get("text"))
        if "content" in content:
            return _content_to_text(content.get("content"))
        return ""
    return str(content).strip()


GATEWAY_ALIASES = {
    "openai-default",
    "openai-compat",
    "litellm",
    "claude-sonnet-4-5",
    "claude-sonnet-4-20250514",
    "claude-3-5-sonnet-latest",
}

UPSTREAM_HEADER = "x-qianxun-upstream-model"
UPSTREAM_BASE_HEADER = "x-qianxun-upstream-base-url"
UPSTREAM_KEY_HEADER = "x-qianxun-upstream-api-key"


def _header_map(data: dict[str, Any]) -> dict[str, str]:
    out: dict[str, str] = {}
    proxy = data.get("proxy_server_request")
    metadata = data.get("metadata")
    candidates: list[Any] = [
        data.get("headers"),
        proxy.get("headers") if isinstance(proxy, dict) else None,
        metadata.get("headers") if isinstance(metadata, dict) else None,
    ]
    for headers in candidates:
        if not isinstance(headers, Mapping):
            continue
        for key, value in headers.items():
            if value is None:
                continue
            out[str(key).lower()] = value if isinstance(value, str) else str(value)
    return out


def _strip_provider_prefix(name: str) -> str:
    raw = (name or "").strip()
    slash = raw.find("/")
    if slash <= 0 or slash >= len(raw) - 1:
        return raw
    provider = raw[:slash].lower()
    if provider in ("openai", "anthropic"):
        return raw[slash + 1 :].strip()
    return raw


def _is_gateway_or_sdk_model(name: str) -> bool:
    n = _strip_provider_prefix(name).lower()
    if not n:
        return True
    if n in GATEWAY_ALIASES:
        return True
    return n.startswith("claude-")


def resolve_upstream_litellm_model(data: dict[str, Any] | None = None) -> str | None:
    """Return openai/<id> for the vendor model LiteLLM should call."""
    headers = _header_map(data or {})
    header_raw = (headers.get(UPSTREAM_HEADER) or "").strip()
    env_raw = (os.getenv("LITELLM_UPSTREAM_MODEL") or "").strip()
    picked = header_raw or env_raw
    ident = _strip_provider_prefix(picked)
    if _is_gateway_or_sdk_model(ident):
        ident = _strip_provider_prefix(env_raw)
    if not ident or _is_gateway_or_sdk_model(ident):
        return None
    return f"openai/{ident}"


ROUTER_CANONICAL = "openai-default"


def _looks_like_claude_sdk_model(name: str) -> bool:
    n = _strip_provider_prefix(name).lower()
    if not n:
        return False
    if n in GATEWAY_ALIASES or n in {"sonnet", "opus", "haiku", "fable"}:
        return True
    return n.startswith("claude")


def apply_upstream_model(data: dict[str, Any]) -> dict[str, Any]:
    """Keep LiteLLM 路由键为已登记别名；上游 model/base/key 写进 litellm_params。"""
    incoming = str(data.get("model") or "").strip()
    if _looks_like_claude_sdk_model(incoming):
        data["model"] = ROUTER_CANONICAL
    headers = _header_map(data)
    vendor = resolve_upstream_litellm_model(data)
    base = (headers.get(UPSTREAM_BASE_HEADER) or os.getenv("OPENAI_UPSTREAM_BASE_URL") or "").strip().rstrip("/")
    key = (headers.get(UPSTREAM_KEY_HEADER) or os.getenv("OPENAI_UPSTREAM_API_KEY") or "").strip()
    params = data.get("litellm_params")
    if not isinstance(params, dict):
        params = None
    if vendor and params is not None:
        params["model"] = vendor
    if base:
        data["api_base"] = base
        if params is not None:
            params["api_base"] = base
    if key:
        data["api_key"] = key
        if params is not None:
            params["api_key"] = key
    return data


def merge_openai_messages(messages: list[Any]) -> list[Any]:
    if not messages:
        return messages

    system_texts: list[str] = []
    rest: list[Any] = []
    extra_system = False
    for index, msg in enumerate(messages):
        if isinstance(msg, dict) and msg.get("role") == "system":
            if index != 0:
                extra_system = True
            text = _content_to_text(msg.get("content"))
            if text:
                system_texts.append(text)
        else:
            rest.append(msg)

    if not system_texts:
        return messages
    if not extra_system and len(system_texts) == 1:
        return messages

    merged = {"role": "system", "content": "\n\n".join(system_texts)}
    return [merged, *rest]


def apply_merge_leading_system(data: dict[str, Any]) -> dict[str, Any]:
    """Rewrite Anthropic and/or OpenAI payloads in place and return data."""
    messages = data.get("messages")
    if not isinstance(messages, list):
        return data

    inline_texts: list[str] = []
    rest: list[Any] = []
    had_inline_system = False
    for msg in messages:
        if isinstance(msg, dict) and msg.get("role") == "system":
            had_inline_system = True
            text = _content_to_text(msg.get("content"))
            if text:
                inline_texts.append(text)
        else:
            rest.append(msg)

    if "system" in data and data["system"] is not None:
        top_text = _content_to_text(data.get("system"))
        if had_inline_system:
            parts = [p for p in (top_text, *inline_texts) if p]
            data["system"] = "\n\n".join(parts)
            data["messages"] = rest
        return data

    data["messages"] = merge_openai_messages(messages)
    return data


class MergeLeadingSystemHandler(CustomLogger):
    async def async_pre_call_hook(self, user_api_key_dict, cache, data, call_type):
        if not isinstance(data, dict):
            return data
        apply_upstream_model(data)
        if not _env_enabled("LITELLM_MERGE_LEADING_SYSTEM", default=True):
            return data
        return apply_merge_leading_system(data)

    async def async_pre_call_deployment_hook(self, kwargs, call_type):
        if isinstance(kwargs, dict):
            apply_upstream_model(kwargs)
        return kwargs


proxy_handler_instance = MergeLeadingSystemHandler()
