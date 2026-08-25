#!/usr/bin/env bash
# 根据 CLAUDE_UPSTREAM_MODE 导出 Claude sidecar 实际使用的 ANTHROPIC_*。
# 由 up.sh / verify.sh 在 docker compose 之前 source（须在 docker/ 目录下执行）。
#
#   anthropic — 直连 Anthropic Messages 兼容上游（现有 ANTHROPIC_BASE_URL）
#   openai    — 经 LiteLLM 转发到 OpenAI Compatible（方案 A）

_qx_env_file_val() {
  local key="$1"
  local v=""
  if [ -f .env ]; then
    v=$(grep -E "^${key}=" .env 2>/dev/null | tail -n 1 | cut -d= -f2-)
    v=${v#\"}; v=${v%\"}; v=${v#\'}; v=${v%\'}
  fi
  printf '%s' "${v}"
}

_qx_env_val() {
  local key="$1" def="${2:-}"
  # shell 已导出的非空值优先
  eval "local cur=\${${key}:-}"
  if [ -n "${cur}" ]; then
    printf '%s' "${cur}"
    return
  fi
  local file_v
  file_v="$(_qx_env_file_val "${key}")"
  if [ -n "${file_v}" ]; then
    printf '%s' "${file_v}"
    return
  fi
  printf '%s' "${def}"
}

CLAUDE_UPSTREAM_MODE="$(_qx_env_val CLAUDE_UPSTREAM_MODE anthropic)"
export CLAUDE_UPSTREAM_MODE

case "${CLAUDE_UPSTREAM_MODE}" in
  openai|openai-compat|litellm)
    export CLAUDE_UPSTREAM_MODE=openai
    LITELLM_MASTER_KEY="$(_qx_env_val LITELLM_MASTER_KEY sk-litellm-local)"
    LITELLM_MODEL_ALIAS="$(_qx_env_val LITELLM_MODEL_ALIAS openai-default)"
    export LITELLM_MASTER_KEY
    export LITELLM_MODEL_ALIAS
    export LITELLM_UPSTREAM_MODEL="$(_qx_env_val LITELLM_UPSTREAM_MODEL openai/gpt-4o-mini)"
    export OPENAI_UPSTREAM_BASE_URL="$(_qx_env_val OPENAI_UPSTREAM_BASE_URL https://api.openai.com/v1)"

    # 上游真实 key：在覆盖 ANTHROPIC_API_KEY 之前从文件读取
    _up_key="$(_qx_env_file_val OPENAI_UPSTREAM_API_KEY)"
    if [ -z "${_up_key}" ]; then
      _up_key="$(_qx_env_file_val OPENAI_API_KEY)"
    fi
    if [ -z "${_up_key}" ]; then
      _up_key="$(_qx_env_file_val ANTHROPIC_API_KEY)"
    fi
    if [ -z "${_up_key}" ] && [ -n "${OPENAI_UPSTREAM_API_KEY:-}" ]; then
      _up_key="${OPENAI_UPSTREAM_API_KEY}"
    fi
    export OPENAI_UPSTREAM_API_KEY="${_up_key}"

    export ANTHROPIC_BASE_URL="http://litellm:4000"
    export ANTHROPIC_API_KEY="${LITELLM_MASTER_KEY}"
    export ANTHROPIC_AUTH_TOKEN="${LITELLM_MASTER_KEY}"
    export ANTHROPIC_MODEL="${LITELLM_MODEL_ALIAS}"
    export QIANXUN_CLAUDE_MODEL="${LITELLM_MODEL_ALIAS}"
    # 避免 LiteLLM 把 thinking 转到上游 /v1/responses（多数 OpenAI Compatible 不兼容）
    if [ -z "${QIANXUN_CLAUDE_THINKING:-}" ] && [ -z "$(_qx_env_file_val QIANXUN_CLAUDE_THINKING)" ]; then
      export QIANXUN_CLAUDE_THINKING=disabled
    else
      export QIANXUN_CLAUDE_THINKING="$(_qx_env_val QIANXUN_CLAUDE_THINKING disabled)"
    fi

    if [ -z "${OPENAI_UPSTREAM_API_KEY}" ]; then
      echo "[claude-upstream] ⚠ CLAUDE_UPSTREAM_MODE=openai 但未配置 OPENAI_UPSTREAM_API_KEY / OPENAI_API_KEY" >&2
    else
      echo "[claude-upstream] 模式=openai → LiteLLM → ${OPENAI_UPSTREAM_BASE_URL} model=${LITELLM_UPSTREAM_MODEL} alias=${LITELLM_MODEL_ALIAS}"
    fi
    ;;
  *)
    export CLAUDE_UPSTREAM_MODE=anthropic
    echo "[claude-upstream] 模式=anthropic → 直连 ANTHROPIC_BASE_URL=$(_qx_env_val ANTHROPIC_BASE_URL '(未设)')"
    ;;
esac
