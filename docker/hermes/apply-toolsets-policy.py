#!/usr/bin/env python3
"""Apply docker/hermes/toolsets-policy.yaml to a Hermes HERMES_HOME tree."""
from __future__ import annotations

import argparse
import copy
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("需要 PyYAML：pip install pyyaml", file=sys.stderr)
    sys.exit(1)

FOOTER_MARK = "\n# ──"
NO_MCP = "no_mcp"
ALWAYS_DISABLED_DEFAULT = ["memory", "session_search"]


def config_paths(home: Path) -> list[Path]:
    out = [home / "config.yaml"]
    profiles = home / "profiles"
    if profiles.is_dir():
        for p in sorted(profiles.iterdir()):
            if p.is_dir():
                out.append(p / "config.yaml")
    return out


def str_list(raw) -> list[str]:
    if not isinstance(raw, list):
        return []
    out: list[str] = []
    seen: set[str] = set()
    for x in raw:
        n = str(x).strip()
        if n and n not in seen:
            seen.add(n)
            out.append(n)
    return out


def split_footer(text: str) -> tuple[str, str]:
    i = text.find(FOOTER_MARK)
    if i < 0:
        return text, ""
    return text[:i], text[i:]


def api_server_list(enabled: list[str], append_no_mcp: bool) -> list[str]:
    out = [n for n in enabled if n and n != NO_MCP]
    if append_no_mcp:
        out.append(NO_MCP)
    return out


def always_disabled_list(policy: dict) -> list[str]:
    raw = str_list(policy.get("always_disabled"))
    return raw if raw else list(ALWAYS_DISABLED_DEFAULT)


def tool_search_from_policy(policy: dict) -> dict | None:
    """Normalize policy.tool_search → Hermes tools.tool_search dict, or None to skip."""
    raw = policy.get("tool_search")
    if raw is None:
        return None
    if raw is True:
        return {"enabled": True}
    if raw is False:
        return {"enabled": False}
    if not isinstance(raw, dict):
        return {"enabled": True}
    out: dict = {}
    if "enabled" in raw:
        out["enabled"] = raw["enabled"]
    else:
        out["enabled"] = True
    for key in ("threshold_pct", "search_default_limit", "max_search_limit", "listing", "listing_max_tokens"):
        if key in raw:
            out[key] = raw[key]
    return out


def apply_tool_search(data: dict, want: dict, overwrite: bool) -> None:
    tools = data.get("tools")
    if not isinstance(tools, dict):
        tools = {}
        data["tools"] = tools
    current = tools.get("tool_search")
    missing = current is None
    if overwrite or missing:
        tools["tool_search"] = dict(want)


def apply_policy_to_config(data: dict, policy: dict) -> dict:
    """Mutate a parsed config.yaml dict; return it."""
    enabled = str_list(policy.get("api_server_enabled"))
    if not enabled:
        raise ValueError("toolsets-policy.yaml 的 api_server_enabled 不能为空")
    always_off = always_disabled_list(policy)
    enabled = [n for n in enabled if n not in set(always_off) and n != NO_MCP]
    if not enabled:
        raise ValueError("toolsets-policy.yaml 的 api_server_enabled 在剔除 always_disabled 后不能为空")
    catalog = str_list(policy.get("known_builtin_toolsets"))
    append_no_mcp = bool(policy.get("append_no_mcp", True))
    sync_cli = bool(policy.get("sync_cli", True))
    overwrite = bool(policy.get("overwrite_existing", True))

    want_api = api_server_list(enabled, append_no_mcp)
    disabled = [n for n in catalog if n not in set(enabled) and n != NO_MCP]
    for n in always_off:
        if n and n != NO_MCP and n not in disabled:
            disabled.append(n)

    pt = data.get("platform_toolsets")
    if not isinstance(pt, dict):
        pt = {}
        data["platform_toolsets"] = pt

    current_api = pt.get("api_server")
    missing = not isinstance(current_api, list)
    if overwrite or missing:
        pt["api_server"] = want_api
        if sync_cli:
            pt["cli"] = list(enabled)

    agent = data.get("agent")
    if not isinstance(agent, dict):
        agent = {}
        data["agent"] = agent
    if overwrite or missing:
        prev = str_list(agent.get("disabled_toolsets"))
        keep = [n for n in prev if n not in set(enabled) and n != NO_MCP]
        merged = list(dict.fromkeys(disabled + keep))
        agent["disabled_toolsets"] = merged

    if catalog and (overwrite or missing):
        known = data.get("known_builtin_toolsets")
        if not isinstance(known, dict):
            known = {}
            data["known_builtin_toolsets"] = known
        known["api_server"] = list(catalog)
        if sync_cli:
            known["cli"] = list(catalog)

    want_ts = tool_search_from_policy(policy)
    if want_ts is not None:
        apply_tool_search(data, want_ts, overwrite)

    return data


def yaml_list_block(indent: int, key: str, items: list[str]) -> str:
    sp = " " * indent
    child = " " * (indent + 2)
    if not items:
        return f"{sp}{key}: []\n"
    return f"{sp}{key}:\n" + "".join(f"{child}- {n}\n" for n in items)


def upsert_child_list(section: str, key: str, items: list[str], indent: int = 2) -> str:
    block = yaml_list_block(indent, key, items)
    pat = re.compile(
        rf"(^[ ]{{{indent}}}{re.escape(key)}:\n(?:[ ]{{{indent + 2}}}- .*\n)*"
        rf"|^[ ]{{{indent}}}{re.escape(key)}:\s*\[\]\s*\n)",
        re.M,
    )
    if pat.search(section):
        return pat.sub(block, section, count=1)
    if not section.endswith("\n"):
        section += "\n"
    return section + block


def extract_top_section(text: str, key: str) -> tuple[int, int] | None:
    m = re.search(rf"^{re.escape(key)}:\n", text, re.M)
    if not m:
        return None
    start = m.start()
    nxt = re.search(r"^[a-zA-Z_][a-zA-Z0-9_]*:", text[m.end():], re.M)
    end = m.end() + nxt.start() if nxt else len(text)
    return start, end


def replace_top_section(text: str, key: str, section: str) -> str:
    loc = extract_top_section(text, key)
    if loc is None:
        if not text.endswith("\n"):
            text += "\n"
        return text + "\n" + section
    start, end = loc
    return text[:start] + section + text[end:]


def yaml_scalar(value) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return str(value)
    s = str(value).replace("\\", "\\\\").replace('"', '\\"')
    if s == "" or any(c in s for c in ":#{}[]&*!|>'\"%@` \t") or s != str(value).strip():
        return f'"{s}"'
    return str(value)


def tool_search_block(want: dict) -> str:
    lines = ["  tool_search:\n"]
    for key, val in want.items():
        lines.append(f"    {key}: {yaml_scalar(val)}\n")
    return "".join(lines)


def upsert_tool_search_section(section: str, want: dict) -> str:
    """Replace or append tools.tool_search under an existing `tools:` section body."""
    block = tool_search_block(want)
    pat = re.compile(
        r"(^[ ]{2}tool_search:\n(?:[ ]{4}.+\n)*)",
        re.M,
    )
    if pat.search(section):
        return pat.sub(block, section, count=1)
    if not section.endswith("\n"):
        section += "\n"
    return section + block


def patch_config_text(text: str, data: dict) -> str:
    """Only rewrite toolset-related blocks; keep the rest of config.yaml."""
    body, footer = split_footer(text if text else "")
    pt = data.get("platform_toolsets") or {}
    agent = data.get("agent") or {}
    known = data.get("known_builtin_toolsets") or {}
    tools = data.get("tools") or {}

    loc = extract_top_section(body, "platform_toolsets")
    if loc is None:
        section = "platform_toolsets:\n"
    else:
        section = body[loc[0] : loc[1]]
    if isinstance(pt.get("cli"), list):
        section = upsert_child_list(section, "cli", pt["cli"])
    if isinstance(pt.get("api_server"), list):
        section = upsert_child_list(section, "api_server", pt["api_server"])
    body = replace_top_section(body, "platform_toolsets", section)

    loc = extract_top_section(body, "agent")
    if loc is None:
        section = "agent:\n"
    else:
        section = body[loc[0] : loc[1]]
    if isinstance(agent.get("disabled_toolsets"), list):
        section = upsert_child_list(section, "disabled_toolsets", agent["disabled_toolsets"])
    body = replace_top_section(body, "agent", section)

    if isinstance(known, dict) and (known.get("api_server") or known.get("cli")):
        loc = extract_top_section(body, "known_builtin_toolsets")
        if loc is None:
            section = "known_builtin_toolsets:\n"
        else:
            section = body[loc[0] : loc[1]]
        if isinstance(known.get("cli"), list):
            section = upsert_child_list(section, "cli", known["cli"])
        if isinstance(known.get("api_server"), list):
            section = upsert_child_list(section, "api_server", known["api_server"])
        body = replace_top_section(body, "known_builtin_toolsets", section)

    want_ts = tools.get("tool_search") if isinstance(tools, dict) else None
    if isinstance(want_ts, dict):
        loc = extract_top_section(body, "tools")
        if loc is None:
            section = "tools:\n" + tool_search_block(want_ts)
        else:
            section = upsert_tool_search_section(body[loc[0] : loc[1]], want_ts)
        body = replace_top_section(body, "tools", section)

    if footer and not body.endswith("\n"):
        body += "\n"
    return body + footer


def already_applied(data: dict, policy: dict) -> bool:
    probe = copy.deepcopy(data)
    apply_policy_to_config(probe, policy)
    return probe == data


def patch_config(path: Path, policy: dict) -> bool:
    original = path.read_text(encoding="utf-8") if path.exists() else ""
    data = yaml.safe_load(split_footer(original)[0]) if original else {}
    if not isinstance(data, dict):
        data = {}
    if path.exists() and already_applied(data, policy):
        return False
    apply_policy_to_config(data, policy)
    updated = patch_config_text(original, data)
    if updated == original:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(updated, encoding="utf-8")
    return True


def self_test() -> None:
    baseline = ["web", "file", "terminal", "code_execution", "delegation"]
    policy = {
        "api_server_enabled": baseline + ["memory"],
        "always_disabled": ["memory", "session_search"],
        "append_no_mcp": True,
        "sync_cli": True,
        "overwrite_existing": True,
        "tool_search": {"enabled": True},
        "known_builtin_toolsets": [
            "file", "web", "terminal", "browser", "bfl", "a2a",
            "code_execution", "delegation", "skills", "memory", "session_search",
        ],
    }
    out = apply_policy_to_config({"platform_toolsets": {"cli": ["browser"]}}, policy)
    assert out["platform_toolsets"]["api_server"] == baseline + ["no_mcp"]
    assert out["platform_toolsets"]["cli"] == baseline
    assert "browser" in out["agent"]["disabled_toolsets"]
    assert "memory" in out["agent"]["disabled_toolsets"]
    assert "session_search" in out["agent"]["disabled_toolsets"]
    assert "skills" in out["agent"]["disabled_toolsets"]
    assert "file" not in out["agent"]["disabled_toolsets"]
    assert "delegation" not in out["agent"]["disabled_toolsets"]
    assert "web" not in out["agent"]["disabled_toolsets"]
    assert out["known_builtin_toolsets"]["api_server"] == policy["known_builtin_toolsets"]
    assert out["known_builtin_toolsets"]["cli"] == policy["known_builtin_toolsets"]
    assert out["tools"]["tool_search"] == {"enabled": True}

    skip = apply_policy_to_config(
        {"platform_toolsets": {"api_server": ["hermes-api-server"]},
         "tools": {"tool_search": {"enabled": False}}},
        {**policy, "overwrite_existing": False},
    )
    assert skip["platform_toolsets"]["api_server"] == ["hermes-api-server"]
    assert skip["tools"]["tool_search"] == {"enabled": False}

    filled = apply_policy_to_config({}, {**policy, "overwrite_existing": False})
    assert filled["platform_toolsets"]["api_server"][-1] == "no_mcp"
    assert filled["platform_toolsets"]["api_server"][:-1] == baseline
    assert filled["tools"]["tool_search"] == {"enabled": True}

    sample = (
        "model:\n  default: qwen-plus\n"
        "agent:\n  max_turns: 500\n  disabled_toolsets:\n    - video\n"
        "platform_toolsets:\n  cli:\n    - browser\n    - file\n"
        "  telegram:\n    - hermes-telegram\n"
        "  api_server:\n    - web\n    - browser\n"
        "known_builtin_toolsets:\n  cli:\n    - file\n    - browser\n"
        "tools:\n  tool_search:\n    enabled: auto\n"
        "\n# ── Security ──\n# keep me\n"
    )
    apply_policy_to_config(out, policy)
    patched = patch_config_text(sample, out)
    assert (
        "  api_server:\n    - web\n    - file\n    - terminal\n"
        "    - code_execution\n    - delegation\n    - no_mcp\n"
    ) in patched
    assert (
        "  cli:\n    - web\n    - file\n    - terminal\n"
        "    - code_execution\n    - delegation\n"
    ) in patched
    assert "telegram:\n    - hermes-telegram" in patched
    assert "max_turns: 500" in patched
    assert "tools:\n  tool_search:\n    enabled: true\n" in patched
    assert "# ── Security ──" in patched
    assert "keep me" in patched
    print("self-test ok")


def main() -> int:
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description="写入精简的 platform_toolsets.api_server")
    parser.add_argument("--home", help="HERMES_HOME（容器内 /opt/data 或宿主 docker/data/hermes）")
    parser.add_argument("--policy", default=str(here / "toolsets-policy.yaml"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    if not args.home:
        print("需要 --home", file=sys.stderr)
        return 1

    home = Path(args.home)
    policy_path = Path(args.policy)
    if not home.is_dir():
        print(f"HERMES_HOME 不存在: {home}", file=sys.stderr)
        return 1
    policy = yaml.safe_load(policy_path.read_text(encoding="utf-8")) or {}
    enabled = str_list(policy.get("api_server_enabled"))
    if not enabled:
        print("toolsets-policy.yaml 的 api_server_enabled 不能为空", file=sys.stderr)
        return 1

    changed = []
    for cfg_path in config_paths(home):
        if cfg_path.name != "config.yaml":
            continue
        if not cfg_path.exists() and cfg_path != home / "config.yaml":
            continue
        if not cfg_path.parent.is_dir():
            continue
        if patch_config(cfg_path, policy):
            changed.append(str(cfg_path))

    print(f"api_server_enabled: {', '.join(enabled)}")
    want_ts = tool_search_from_policy(policy)
    if want_ts is not None:
        print(f"tool_search: {want_ts}")
    if changed:
        print("已更新:")
        for c in changed:
            print(f"  {c}")
    else:
        print("config.yaml 工具集无需改动")
    return 0


if __name__ == "__main__":
    sys.exit(main())
