#!/usr/bin/env python3
"""Export reviewed Git candidates without Git history or local private files.

The existing privacy and Markdown checks run against the exact staged bytes
included in the archive. They are heuristics, not a guarantee against disclosure.
Output is restricted to ignored ZIP files inside artifacts/. No source is changed.
"""
from __future__ import annotations

import argparse
import importlib.util
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import unicodedata
import zipfile


class ExportError(Exception):
    """A redacted failure safe to display without file contents or paths."""


def load_check(filename):
    spec = importlib.util.spec_from_file_location(
        "export_" + filename.replace("-", "_"), Path(__file__).with_name(filename)
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def validate_relative(relative: Path):
    value = relative.as_posix()
    if (relative.is_absolute() or not relative.parts or ".." in relative.parts
            or "\\" in value or ":" in value
            or any(ord(char) < 32 or ord(char) == 127 for char in value)
            or any(part.endswith((".", " ")) for part in relative.parts)
            or any(re.fullmatch(r"(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?", part, re.I)
                   for part in relative.parts)):
        raise ExportError("Unsafe archive path; export refused.")


def forbidden_path(relative: Path, privacy) -> bool:
    parts = {part.casefold() for part in relative.parts}
    blocked = {".git", ".local", ".ssh", ".aws", ".gnupg", "artifacts", "dist",
               "backup", "backups", "dump", "dumps", "privatekey", "privatekeys"}
    if parts.intersection(blocked) or privacy.forbidden_file(relative):
        return True
    for part in parts:
        if (part == ".env" or part.startswith(".env.")) and part != ".env.example":
            return True
        if ("备份" in part
                or re.search(r"(?:^|[_.-])(?:backups?|dumps?|private[_-]?keys?)(?:$|[_.-])", part)):
            return True
    name = relative.name.casefold()
    return (name.startswith(("id_rsa", "id_ed25519", "id_ecdsa", "id_dsa"))
            or re.search(r"(?:^|[_.-])(?:backup|dump|private[_-]?key)(?:$|[_.-])", name) is not None
            or name.endswith((".bak", ".backup", ".old", ".orig", ".tar", ".gz", ".7z", ".rar")))


def checked_stat(root: Path, relative: Path):
    """Reject links in every component, including dangling links and parents."""
    current = root
    for index, part in enumerate(relative.parts):
        current /= part
        try:
            info = current.lstat()
        except FileNotFoundError:
            return None
        if stat.S_ISLNK(info.st_mode):
            raise ExportError("Symbolic link candidate; export refused.")
        if index < len(relative.parts) - 1 and not stat.S_ISDIR(info.st_mode):
            raise ExportError("Invalid source directory; export refused.")
    if not stat.S_ISREG(info.st_mode):
        raise ExportError("Non-regular source candidate; export refused.")
    return info


def signature(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_mode


def read_source(root: Path, relative: Path, expected):
    current = checked_stat(root, relative)
    if current is None or signature(current) != signature(expected):
        raise ExportError("Source changed during export; retry after edits finish.")
    descriptor = os.open(root / relative, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(descriptor, "rb") as stream:
        if signature(os.fstat(stream.fileno())) != signature(expected):
            raise ExportError("Source changed during export; retry after edits finish.")
        content = stream.read()
        if signature(os.fstat(stream.fileno())) != signature(expected):
            raise ExportError("Source changed during export; retry after edits finish.")
    return content


def output_path(root: Path, requested: Path) -> Path:
    output = requested if requested.is_absolute() else root / requested
    # Do not resolve symlinks before inspecting them.
    try:
        relative = output.relative_to(root)
    except ValueError:
        raise ExportError("Output must be an ignored ZIP inside artifacts/.") from None
    validate_relative(relative)
    if len(relative.parts) != 2 or relative.parts[0] != "artifacts" or output.suffix != ".zip":
        raise ExportError("Output must be an ignored ZIP inside artifacts/.")
    for path in (output.parent, output):
        if path.is_symlink():
            raise ExportError("Symbolic link output; export refused.")
    result = subprocess.run(
        ["git", "-C", str(root), "check-ignore", "-q", "--", relative.as_posix()],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        raise ExportError("Output must be ignored by Git; export refused.")
    if output.exists() and not output.is_file():
        raise ExportError("Invalid output file; export refused.")
    return output


def export_source(root: Path, requested: Path = Path("artifacts/ContractSearch-source.zip")):
    root = root.resolve(strict=True)
    privacy = load_check("check-public-content.py")
    links = load_check("check-doc-links.py")
    output = output_path(root, requested)
    candidates = privacy.git_candidates(root)
    files = []
    names = set()
    # Complete preflight before reading any source content.
    for relative in candidates:
        validate_relative(relative)
        info = checked_stat(root, relative)
        if info is None:
            continue  # Deleted tracked files are absent from the working source.
        if forbidden_path(relative, privacy) or root / relative == output:
            raise ExportError("Forbidden publication candidate; export refused.")
        if info.st_size > 5 * 1024 * 1024:
            raise ExportError("Oversized publication candidate; export refused.")
        name = unicodedata.normalize("NFC", relative.as_posix()).casefold()
        if name in names:
            raise ExportError("Ambiguous archive paths; export refused.")
        names.add(name)
        files.append((relative, info))
    if not files:
        raise ExportError("No publication candidates; export refused.")

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".source-export-", dir=output.parent) as temporary:
        stage = Path(temporary) / "source"
        stage.mkdir()
        for relative, info in files:
            destination = stage / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(read_source(root, relative, info))
        # These fresh check-module instances inspect only the frozen inventory.
        # Staging also catches links to existing but unexported ignored files.
        live_candidates = [relative for relative, _ in files]
        privacy.git_candidates = lambda _: live_candidates
        links._inventory.git_candidates = lambda _: live_candidates
        privacy_count = len(privacy.scan(stage))
        links_count = len(links.scan(stage))
        if privacy_count or links_count:
            raise ExportError(f"Publication gates failed: privacy={privacy_count}, links={links_count}.")

        archive_path = Path(temporary) / "source.zip"
        with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for relative, info in files:
                entry = zipfile.ZipInfo("ContractSearch/" + relative.as_posix(), (1980, 1, 1, 0, 0, 0))
                entry.create_system = 3
                entry.external_attr = (0o100755 if info.st_mode & 0o111 else 0o100644) << 16
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, (stage / relative).read_bytes())
        # An existing ignored archive is replaced only after both gates succeed.
        output_path(root, requested)
        os.replace(archive_path, output)
    return len(files), output.stat().st_size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path, default=Path("artifacts/ContractSearch-source.zip"))
    args = parser.parse_args()
    try:
        count, size = export_source(args.root, args.output)
    except ExportError as error:
        print(str(error), file=sys.stderr)
        return 1
    except (OSError, ValueError, UnicodeError, subprocess.CalledProcessError):
        print("Source export failed; verify the checkout and file access.", file=sys.stderr)
        return 2
    print(f"files={count} bytes={size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
