#!/usr/bin/env python3
"""Apply docker/hermes/skills-policy.yaml to a Hermes HERMES_HOME tree."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("需要 PyYAML：pip install pyyaml", file=sys.stderr)
    sys.exit(1)

MARKER_NAME = ".no-bundled-skills"
SKIP_DIR_NAMES = {
    ".hub",
    "references",
    "assets",
    "scripts",
    "templates",
    "examples",
}


def skill_name_from_md(md: Path) -> str:
    text = md.read_text(encoding="utf-8", errors="replace")[:4000]
    m = re.search(r"^---\s*\n(.*?)\n---", text, re.S)
    name = md.parent.name
    if m:
        nm = re.search(r"^name:\s*[\"']?([^\"'\n]+)", m.group(1), re.M)
        if nm:
            name = nm.group(1).strip()
    return name


def discover_skill_names(home: Path) -> set[str]:
    names: set[str] = set()
    roots = [home / "skills"]
    profiles = home / "profiles"
    if profiles.is_dir():
        for p in profiles.iterdir():
            if p.is_dir():
                roots.append(p / "skills")
    for root in roots:
        if not root.is_dir():
            continue
        for md in root.rglob("SKILL.md"):
            if any(part in SKIP_DIR_NAMES for part in md.parts):
                continue
            names.add(skill_name_from_md(md))
    return names


def config_paths(home: Path) -> list[Path]:
    out = [home / "config.yaml"]
    profiles = home / "profiles"
    if profiles.is_dir():
        for p in profiles.iterdir():
            cfg = p / "config.yaml"
            if p.is_dir() and cfg.is_file():
                out.append(cfg)
    return out


def marker_dirs(home: Path) -> list[Path]:
    out = [home]
    profiles = home / "profiles"
    if profiles.is_dir():
        out.extend(p for p in profiles.iterdir() if p.is_dir())
    return out


def disabled_block(disabled: list[str]) -> str:
    if not disabled:
        return "  disabled: []\n"
    return "  disabled:\n" + "".join(f"    - {n}\n" for n in disabled)


def patch_config_text(text: str, disabled: list[str]) -> str:
    """只替换 skills.disabled，保留其余字段与文件末尾注释。"""
    block = disabled_block(disabled)
    m = re.search(r"(^skills:\n(?:  .*\n)*?)(  disabled:\n(?:    - .*\n)*|  disabled:\s*\[\]\s*\n)", text, re.M)
    if m:
        return text[: m.start(2)] + block + text[m.end(2) :]
    if re.search(r"^skills:\s*$", text, re.M):
        return re.sub(r"^skills:\s*$", "skills:\n" + block.rstrip("\n"), text, count=1, flags=re.M)
    if not text.endswith("\n"):
        text += "\n"
    return text + "\nskills:\n" + block


def patch_config(path: Path, disabled: list[str]) -> bool:
    original = path.read_text(encoding="utf-8") if path.exists() else ""
    if original:
        data = yaml.safe_load(original) or {}
        before = []
        if isinstance(data, dict) and isinstance(data.get("skills"), dict):
            before = [str(x).strip() for x in (data["skills"].get("disabled") or []) if str(x).strip()]
        if before == disabled:
            return False
        updated = patch_config_text(original, disabled)
    else:
        updated = "skills:\n" + disabled_block(disabled)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(updated, encoding="utf-8")
    return True


def write_marker(directory: Path, template: Path) -> None:
    dest = directory / MARKER_NAME
    text = template.read_text(encoding="utf-8") if template.is_file() else (
        "This profile opted out of bundled-skill seeding.\n"
    )
    dest.write_text(text, encoding="utf-8")


def main() -> int:
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description="只启用 skills-policy.yaml 中的 Hermes 技能")
    parser.add_argument("--home", required=True, help="HERMES_HOME（容器内 /opt/data 或宿主 docker/data/hermes）")
    parser.add_argument("--policy", default=str(here / "skills-policy.yaml"))
    parser.add_argument("--marker-template", default=str(here / MARKER_NAME))
    args = parser.parse_args()

    home = Path(args.home)
    policy_path = Path(args.policy)
    if not home.is_dir():
        print(f"HERMES_HOME 不存在: {home}", file=sys.stderr)
        return 1
    policy = yaml.safe_load(policy_path.read_text(encoding="utf-8")) or {}
    enabled = {str(x).strip() for x in (policy.get("enabled") or []) if str(x).strip()}
    if not enabled:
        print("skills-policy.yaml 的 enabled 不能为空", file=sys.stderr)
        return 1

    discovered = discover_skill_names(home)
    disabled = sorted((discovered | enabled) - enabled)
    # 已在 config 里禁用、但目录已删的名字也保留，避免被重新打开
    for cfg_path in config_paths(home):
        if not cfg_path.is_file():
            continue
        data = yaml.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
        old = []
        if isinstance(data, dict) and isinstance(data.get("skills"), dict):
            old = data["skills"].get("disabled") or []
        for name in old:
            n = str(name).strip()
            if n and n not in enabled:
                disabled.append(n)
    disabled = sorted(set(disabled))

    changed = []
    for cfg_path in config_paths(home):
        if cfg_path.name != "config.yaml":
            continue
        if not cfg_path.parent.is_dir():
            continue
        if patch_config(cfg_path, disabled):
            changed.append(str(cfg_path))

    if policy.get("write_opt_out_marker", True):
        template = Path(args.marker_template)
        for d in marker_dirs(home):
            write_marker(d, template)

    print(f"enabled: {', '.join(sorted(enabled))}")
    print(f"disabled: {len(disabled)} 项")
    if changed:
        print("已更新:")
        for c in changed:
            print(f"  {c}")
    else:
        print("config.yaml 无需改动")
    print(f"已写入 {MARKER_NAME}（默认 profile 与 named profiles）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
