#!/usr/bin/env python3
"""Heuristic pre-publication checks for Git-tracked and non-ignored candidates.

Only file names, line numbers and categories are printed. Ignored local files
are never inventoried; forbidden files are reported without opening them.
This is not a secret-history audit and cannot guarantee absence of private data.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys


PRIVATE_KEY = re.compile(r"-----BEGIN (?:[A-Z0-9]+ )?PRIVATE KEY-----")
PERSONAL_PATH = re.compile(r"(?:/Users|/home)/[A-Za-z0-9_.-]+/|[A-Za-z]:\\Users\\[^\\\s]+\\")
EMAIL = re.compile(r"(?<![\w.+-])[A-Za-z0-9_.+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})(?![\w.-])")
PROVIDER_TOKEN = re.compile(r"\b(?:sk-[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[A-Z0-9]{16})\b")
SECRET_LITERAL = re.compile(
    r'''["']?([\w.-]*(?:api[_-]?key|secret|password|token)[\w.-]*)["']?\s*[:=]\s*["']([^"'\r\n]+)["']''',
    re.IGNORECASE,
)
URL_CREDENTIAL = re.compile(r"\b(?:https?|postgres(?:ql)?|mysql|mongodb)://([^\s/@:]+):([^\s/@]+)@", re.IGNORECASE)
CONFIG_LITERAL = re.compile(r'''^\s*(?:export\s+)?([\w.-]*(?:api[_-]?key|secret|password|token)[\w.-]*)\s*[:=]\s*([^\s#"']+)\s*(?:#.*)?$''', re.IGNORECASE)


def git_candidates(root: Path) -> list[Path]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
    )
    return sorted({Path(item.decode("utf-8", errors="surrogateescape"))
                   for item in result.stdout.split(b"\0") if item})


def forbidden_file(path: Path) -> bool:
    name = path.name.lower()
    parts = {part.lower() for part in path.parts}
    value = path.as_posix().lower()
    if value.startswith(("experiments/", "docs/archive/", "docs/agentic-rag/research/",
                         "docs/agentic-rag/eval/", "docs/agentic-rag/adr/")):
        return True
    if name == "livepilotbridgetest.java":
        return True
    if (name == ".env" or name.startswith(".env.")) and name != ".env.example":
        return True
    if parts.intersection({"backups", "dumps", "uploads", "logs", "nacos-data", "node_modules", ".venv", "venv", "__pycache__", "target"}):
        return True
    if str(path).startswith("scse-discover/data/") or ("backend" in parts and "data" in parts):
        return True
    # The reviewed bootstrap schema and synthetic demo are source assets, not
    # live database exports. Other SQL under database/ remains disallowed.
    if "database" in parts and name.endswith(".sql") and not str(path).startswith("database/bootstrap/"):
        return True
    return name == ".ds_store" or name.endswith((".dump", ".sql.gz", ".sqlite", ".sqlite3", ".db", ".parquet", ".log", ".pid", ".trace", ".pem", ".key", ".p12", ".pfx", ".jks", ".zip", ".tgz"))


def is_placeholder(value: str) -> bool:
    value = value.strip()
    lower = value.lower()
    return (
        not value
        or any(marker in value for marker in ("${", "#{", "{{", "<", ">"))
        or lower.startswith(("test-", "test_", "dummy", "example", "replace", "change_me", "changeme", "your_", "your-"))
        or bool(re.search(r"(?:^|[-_ ])(?:test|synthetic|fixture)(?:$|[-_ ])", lower))
        or all(re.fullmatch(r"key-(?:one|two|three)", item.strip()) for item in value.split(",") if item.strip())
        or lower in {"+", "sk-", "ghp_", "authorization", "bearer", "x-agent-internal-token", "x-contract-import-token"}
        or bool(re.fullmatch(r"[A-Z][A-Z_]+", value))
    )


def find_line_categories(line: str, *, upstream_notice: bool = False, test_fixture: bool = False) -> set[str]:
    categories = set()
    if PRIVATE_KEY.search(line):
        categories.add("private-key")
    if PERSONAL_PATH.search(line):
        categories.add("personal-path")
    if not upstream_notice:
        for match in EMAIL.finditer(line):
            domain = match.group(1).lower()
            if domain not in {"example.com", "example.org", "example.net", "example.test", "localhost.localdomain"} and not domain.endswith((".example", ".invalid")):
                categories.add("non-example-email")
    # A recognized provider-token shape remains suspicious even when it happens
    # to be all uppercase (for example an AWS access-key identifier).
    if PROVIDER_TOKEN.search(line):
        categories.add("provider-token")
    for match in SECRET_LITERAL.finditer(line):
        key, value = match.group(1).lower(), match.group(2)
        # Monaco token kinds are syntax highlighting metadata, not credentials.
        if key in {"tokenpostfix", "defaulttoken"} or (key == "token" and value in {
            "comment", "keyword", "string", "number", "operator", "identifier"
        }):
            continue
        if not is_placeholder(value):
            categories.add("credential-literal")
    for match in URL_CREDENTIAL.finditer(line):
        if test_fixture and match.group(2) in {"secret", "password"}:
            continue  # Explicitly synthetic rejection fixtures; not a file-wide exemption.
        if not is_placeholder(match.group(2)):
            categories.add("credential-in-url")
    return categories


def scan(root: Path) -> list[tuple[str, int, str]]:
    findings = []
    for relative in git_candidates(root):
        path = root / relative
        if forbidden_file(relative):
            findings.append((relative.as_posix(), 1, "forbidden-public-file"))
            continue  # Never read a tracked .env, dump, private key or upload.
        if path.is_symlink():
            findings.append((relative.as_posix(), 1, "symlink-review-required"))
            continue
        if not path.is_file():
            continue  # Deleted working-tree files are absent from the proposed result.
        if path.stat().st_size > 5_000_000:
            findings.append((relative.as_posix(), 1, "large-file-review-required"))
            continue
        content = path.read_bytes()
        if b"\0" in content:
            continue
        # Upstream notices retain their authors' contact attribution. Only the
        # email rule is exempted; secrets and private paths are still inspected.
        notice = bool(re.search(r"(?:LICENSE|NOTICE|COPYING)", path.name, re.IGNORECASE))
        fixture = bool({"test", "tests"}.intersection(relative.parts))
        for number, line in enumerate(content.decode("utf-8", errors="replace").splitlines(), 1):
            categories = find_line_categories(line, upstream_notice=notice, test_fixture=fixture)
            if path.name == ".env.example" or path.suffix in {".yml", ".yaml", ".properties"}:
                entry = CONFIG_LITERAL.match(line)
                if entry and not is_placeholder(entry.group(2)):
                    categories.add("credential-literal")
            for category in sorted(categories):
                findings.append((relative.as_posix(), number, category))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        findings = scan(args.root.resolve())
    except (OSError, subprocess.CalledProcessError):
        print("Public-content inventory failed; verify the Git checkout.", file=sys.stderr)
        return 2
    for path, line, category in findings:
        print(f"{path}:{line}: {category}")
    print(f"Public-content check: {len(findings)} finding(s). Heuristic only; Git history is not scanned.")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
