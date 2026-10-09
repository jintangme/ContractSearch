import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SPEC = importlib.util.spec_from_file_location(
    "source_export", Path(__file__).resolve().parents[1] / "export-public-source.py"
)
exporter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(exporter)


class ExportSourceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self.write(".gitignore", "artifacts/\n.env\n.local/\nnode_modules/\ndist/\n")
        self.output = self.root / "artifacts/ContractSearch-source.zip"

    def write(self, relative, content):
        destination = self.root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content)
        return destination

    def track(self, relative):
        subprocess.run(["git", "-C", str(self.root), "add", "-f", relative], check=True)

    def test_ignored_private_files_and_history_are_neither_read_nor_packed(self):
        self.write("README.md", "![Screenshot](docs/screenshot.png)\n")
        self.write(".env.example", "API_KEY=replace_with_generated_value\n")
        self.write("database/bootstrap/demo.sql", "SELECT 1;\n")
        image = self.root / "docs/screenshot.png"
        image.parent.mkdir()
        image.write_bytes(b"\x89PNG\r\n\x1a\n\x00image-fixture")
        private = [self.write(name, "DO_NOT_READ") for name in (
            ".env", ".local/private.txt", "node_modules/private.txt", "dist/private.txt",
            ".git/private-fixture",
        )]
        original_read = exporter.read_source
        original_bytes = Path.read_bytes
        original_text = Path.read_text
        read_paths = []

        def guarded_source(root, relative, info):
            path = root / relative
            self.assertNotIn(path, private)
            self.assertNotIn(".git", relative.parts)
            read_paths.append(relative.as_posix())
            return original_read(root, relative, info)

        def guarded_bytes(path):
            self.assertNotIn(path, private)
            return original_bytes(path)

        def guarded_text(path, *args, **kwargs):
            self.assertNotIn(path, private)
            return original_text(path, *args, **kwargs)

        with patch.object(exporter, "read_source", guarded_source), \
                patch.object(Path, "read_bytes", guarded_bytes), \
                patch.object(Path, "read_text", guarded_text):
            count, size = exporter.export_source(self.root)
        expected = {".gitignore", ".env.example", "README.md", "database/bootstrap/demo.sql", "docs/screenshot.png"}
        self.assertEqual(set(read_paths), expected)
        self.assertEqual(count, len(expected))
        self.assertEqual(size, self.output.stat().st_size)
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(set(archive.namelist()), {"ContractSearch/" + name for name in expected})
            for entry in archive.infolist():
                self.assertEqual(entry.date_time, (1980, 1, 1, 0, 0, 0))
                self.assertEqual(entry.extra, b"")
                self.assertEqual(entry.comment, b"")
                self.assertNotIn(str(self.root), entry.filename)

    def test_deleted_tracked_files_are_skipped_even_when_forbidden(self):
        for name in ("deleted.py", ".env"):
            path = self.write(name, "deleted fixture")
            self.track(name)
            path.unlink()
        count, _ = exporter.export_source(self.root)
        self.assertEqual(count, 1)

    def test_forbidden_tracked_files_fail_before_any_content_read(self):
        for name in (".env", ".local/private.txt", "node_modules/item.js", "dist/item.js",
                     "backup/export.txt", "database/live.sql", "key.private-key.txt", "id_rsa"):
            with self.subTest(name=name):
                path = self.write(name, "DO_NOT_READ")
                self.track(name)
                with patch.object(exporter, "read_source", side_effect=AssertionError("Source must not be read")):
                    with self.assertRaises(exporter.ExportError):
                        exporter.export_source(self.root)
                path.unlink()
        self.assertFalse(self.output.exists())

    def test_file_and_parent_symlinks_are_rejected(self):
        target = self.write("real.txt", "safe fixture")
        link = self.root / "linked.txt"
        link.symlink_to(target)
        with self.assertRaises(exporter.ExportError):
            exporter.export_source(self.root)
        link.unlink()
        original = self.write("original/file.txt", "safe fixture")
        self.track("original/file.txt")
        original.unlink()
        original.parent.rmdir()
        (self.root / "original").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(exporter.ExportError):
            exporter.export_source(self.root)

    def test_unsafe_archive_names_and_sensitive_components_are_rejected(self):
        privacy = exporter.load_check("check-public-content.py")
        for name in ("../escape", "/absolute", "C:drive.txt", "windows\\path", "line\nname",
                     "trailing.", "CON", "aux.txt"):
            with self.subTest(name=name), self.assertRaises(exporter.ExportError):
                exporter.validate_relative(Path(name))
        for name in (".git/config", "nested/.Git/config", "nested/.env/value", "dump/export.csv",
                     "privatekey/value", "private-keys/value", "备份/export.txt"):
            self.assertTrue(exporter.forbidden_path(Path(name), privacy))

    def test_real_privacy_and_documentation_gates_prevent_export(self):
        unsafe = self.write("unsafe.py", 'API_KEY="' + "sk-" + "a" * 32 + '"\n')
        with self.assertRaisesRegex(exporter.ExportError, r"privacy=[1-9]"):
            exporter.export_source(self.root)
        unsafe.unlink()
        readme = self.write("README.md", "[Missing](missing.md)\n")
        with self.assertRaisesRegex(exporter.ExportError, r"links=1"):
            exporter.export_source(self.root)
        # Even an existing ignored target would be missing from the published ZIP.
        self.write(".local/notes.md", "DO_NOT_READ")
        readme.write_text("[Private](.local/notes.md)\n")
        with self.assertRaisesRegex(exporter.ExportError, r"links=1"):
            exporter.export_source(self.root)
        self.assertFalse(self.output.exists())

    def test_output_cannot_overwrite_source_or_follow_symlink(self):
        source = self.write("source.zip", "preserve input")
        with self.assertRaises(exporter.ExportError):
            exporter.export_source(self.root, Path("source.zip"))
        self.assertEqual(source.read_text(), "preserve input")
        source.unlink()
        destination = self.write("keep.txt", "preserve target")
        self.output.parent.mkdir()
        self.output.symlink_to(destination)
        with self.assertRaises(exporter.ExportError):
            exporter.export_source(self.root)
        self.assertEqual(destination.read_text(), "preserve target")
        self.output.unlink()
        self.output.parent.rmdir()
        self.output.parent.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(exporter.ExportError):
            exporter.export_source(self.root)

    def test_repeated_export_is_deterministic_and_does_not_include_output(self):
        script = self.write("scripts/run.sh", "#!/bin/sh\nexit 0\n")
        script.chmod(0o755)
        first_count, _ = exporter.export_source(self.root)
        first = self.output.read_bytes()
        second_count, _ = exporter.export_source(self.root)
        self.assertEqual(first_count, second_count)
        self.assertEqual(first, self.output.read_bytes())
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(archive.getinfo("ContractSearch/scripts/run.sh").external_attr >> 16, 0o100755)

    def test_cli_only_reports_counts_and_redacted_failure(self):
        output = io.StringIO()
        with patch("sys.argv", ["export", "--root", str(self.root)]), contextlib.redirect_stdout(output):
            self.assertEqual(exporter.main(), 0)
        self.assertRegex(output.getvalue(), r"^files=1 bytes=\d+\n$")
        self.write("sensitive-filename.py", 'API_KEY="' + "sk-" + "a" * 32 + '"\n')
        error = io.StringIO()
        with patch("sys.argv", ["export", "--root", str(self.root)]), contextlib.redirect_stderr(error):
            self.assertEqual(exporter.main(), 1)
        self.assertNotIn(str(self.root), error.getvalue())
        self.assertNotIn("sensitive-filename", error.getvalue())
        self.assertRegex(error.getvalue(), r"privacy=[1-9]")

    def test_failed_reexport_preserves_previous_archive(self):
        exporter.export_source(self.root)
        previous = self.output.read_bytes()
        self.write("README.md", "[Missing](missing.md)\n")
        with self.assertRaises(exporter.ExportError):
            exporter.export_source(self.root)
        self.assertEqual(self.output.read_bytes(), previous)

    def test_file_replaced_after_preflight_is_rejected_before_reading(self):
        source = self.write("source.py", "original source")
        relative = Path("source.py")
        before = source.stat()
        source.unlink()
        source.write_text("replacement source")
        with self.assertRaises(exporter.ExportError):
            exporter.read_source(self.root, relative, before)


if __name__ == "__main__":
    unittest.main()
