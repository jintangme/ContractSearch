#!/usr/bin/env python3
"""Check local Markdown link/image targets in Git publication candidates.

External URLs and fragment anchors are not fetched or validated. Code examples
are ignored. Relative targets must exist inside the repository.
"""
from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

_spec = importlib.util.spec_from_file_location("public_content_inventory", Path(__file__).with_name("check-public-content.py"))
_inventory = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_inventory)

INLINE = re.compile(r'''!?\[[^\]\n]*\]\(\s*(<[^>\n]+>|[^\s)]+)(?:\s+["'][^\n]*?["'])?\s*\)''')
REFERENCE = re.compile(r"^\s{0,3}\[[^\]\n]+\]:\s*(<[^>\n]+>|\S+)")


def markdown_targets(content: str):
    fence = None
    for number, raw in enumerate(content.splitlines(), 1):
        marker = re.match(r"^\s{0,3}(`{3,}|~{3,})", raw)
        if marker:
            delimiter = marker.group(1)
            if fence is None:
                fence = delimiter
            elif delimiter[0] == fence[0] and len(delimiter) >= len(fence):
                fence = None
            continue
        if fence is not None or raw.startswith("    ") or raw.startswith("\t"):
            continue
        line = re.sub(r"(`+).*?\1", "", raw)
        for match in INLINE.finditer(line):
            yield number, match.group(1).strip("<>")
        reference = REFERENCE.match(line)
        if reference:
            yield number, reference.group(1).strip("<>")


def scan(root: Path) -> list[tuple[str, int, str]]:
    findings = []
    for relative in _inventory.git_candidates(root):
        path = root / relative
        if path.suffix.lower() != ".md" or path.is_symlink() or not path.is_file():
            continue
        for line, target in markdown_targets(path.read_text(encoding="utf-8")):
            parts = urlsplit(target)
            if parts.scheme or parts.netloc or not parts.path:
                continue
            decoded = unquote(parts.path)
            resolved = (root / decoded.lstrip("/") if decoded.startswith("/") else path.parent / decoded).resolve()
            if not resolved.is_relative_to(root):
                findings.append((relative.as_posix(), line, "target-outside-repository"))
            elif not resolved.exists():
                findings.append((relative.as_posix(), line, "missing-local-target"))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        findings = scan(args.root.resolve())
    except (OSError, ValueError, subprocess.CalledProcessError):
        print("Documentation inventory failed; verify the checkout and Markdown encoding.", file=sys.stderr)
        return 2
    for path, line, category in findings:
        print(f"{path}:{line}: {category}")
    print(f"Documentation links: {len(findings)} broken local target(s).")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
