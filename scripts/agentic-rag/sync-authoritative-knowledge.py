#!/usr/bin/env python3
"""Fetch a pinned allowlist pack and optionally ingest it through Agent Service."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ALLOWED_REPOSITORIES = {
    ("ethereum", "ERCs"),
    ("ethereum", "EIPs"),
    ("argotorg", "solidity"),
    ("OpenZeppelin", "openzeppelin-contracts"),
    ("OWASP", "www-project-smart-contract-top-10"),
}
MAX_DOCUMENT_BYTES = 2_000_000
MIN_DOCUMENT_BYTES = 500


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", default="docs/agentic-rag/knowledge-sources-v1.json")
    parser.add_argument("--endpoint", default="http://127.0.0.1:18082/internal/agent/v1/knowledge/references")
    parser.add_argument("--token-env", default="AGENT_INTERNAL_TOKEN")
    parser.add_argument("--batch-size", type=int, default=10)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def validate_source(source: dict) -> None:
    parsed = urllib.parse.urlparse(source["url"])
    parts = parsed.path.split("/")
    if parsed.scheme != "https" or parsed.hostname != "raw.githubusercontent.com":
        raise ValueError(f"unapproved host for {source['sourceKey']}")
    if len(parts) < 5 or (parts[1], parts[2]) not in ALLOWED_REPOSITORIES:
        raise ValueError(f"unapproved repository for {source['sourceKey']}")
    if parts[3] != source["version"] or len(parts[3]) != 40:
        raise ValueError(f"source is not pinned to declared commit: {source['sourceKey']}")


def fetch(source: dict) -> dict:
    validate_source(source)
    request = urllib.request.Request(
        source["url"], headers={"User-Agent": "ContractSearch-KnowledgeSync/1.0"}
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read(MAX_DOCUMENT_BYTES + 1)
    if len(raw) > MAX_DOCUMENT_BYTES:
        raise ValueError(f"document exceeds size limit: {source['sourceKey']}")
    if len(raw) < MIN_DOCUMENT_BYTES:
        raise ValueError(f"document is likely a redirect or placeholder: {source['sourceKey']}")
    content = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n").rstrip()
    if not content:
        raise ValueError(f"empty document: {source['sourceKey']}")
    digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
    return {
        "sourceKey": source["sourceKey"],
        "title": source["title"],
        "sourceUrl": source["url"],
        "sourceKind": source["sourceKind"],
        "version": source["version"],
        "license": source["license"],
        "format": source["format"],
        "contentHash": digest,
        "fetchedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "content": content,
    }


def post(endpoint: str, token: str, documents: list[dict]) -> dict:
    payload = json.dumps({"documents": documents}, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Agent-Internal-Token": token,
            "User-Agent": "ContractSearch-KnowledgeSync/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> int:
    args = parse_args()
    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    documents = []
    for source in manifest["sources"]:
        document = fetch(source)
        documents.append(document)
        print(json.dumps({
            "sourceKey": document["sourceKey"],
            "bytes": len(document["content"].encode("utf-8")),
            "sha256": document["contentHash"],
        }))

    if args.dry_run:
        print(json.dumps({"packId": manifest["packId"], "fetched": len(documents), "mode": "DRY_RUN"}))
        return 0

    token = os.getenv(args.token_env, "").strip()
    if len(token) < 24:
        raise ValueError(f"{args.token_env} must contain the trusted Agent token")
    totals = {"received": 0, "insertedSources": 0, "insertedDocuments": 0,
              "insertedChunks": 0, "skippedSources": 0}
    batch_size = max(1, min(args.batch_size, 25))
    for index in range(0, len(documents), batch_size):
        result = post(args.endpoint, token, documents[index:index + batch_size])
        for key in totals:
            totals[key] += int(result.get(key, 0))
    print(json.dumps({"packId": manifest["packId"], **totals}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"knowledge sync failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
