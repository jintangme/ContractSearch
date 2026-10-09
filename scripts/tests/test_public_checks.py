import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPTS = Path(__file__).resolve().parents[1]


def load_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


privacy = load_module("privacy_check", "check-public-content.py")
links = load_module("link_check", "check-doc-links.py")


class CheckoutTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return path


class PublicContentTest(CheckoutTest):
    def test_ignored_local_credentials_are_not_read(self):
        self.write(".gitignore", ".env\nbackups/\n")
        self.write(".env", "API_KEY=" + "sk-" + "a" * 32)
        self.write("backups/private.dump", "private data")
        original = Path.read_bytes

        def guarded(path):
            if path.name in {".env", "private.dump"}:
                raise AssertionError("Ignored private contents must not be read")
            return original(path)

        with patch.object(Path, "read_bytes", guarded):
            self.assertEqual(privacy.scan(self.root), [])

    def test_force_tracked_env_is_reported_without_reading_contents(self):
        self.write(".gitignore", ".env\n")
        self.write(".env", "DO_NOT_READ")
        subprocess.run(["git", "-C", str(self.root), "add", "-f", ".env"], check=True)
        original = Path.read_bytes

        def guarded(path):
            if path.name == ".env":
                raise AssertionError("Forbidden contents must not be read")
            return original(path)

        with patch.object(Path, "read_bytes", guarded):
            self.assertEqual(privacy.scan(self.root), [(".env", 1, "forbidden-public-file")])

    def test_findings_are_redacted_and_include_untracked_candidates(self):
        secret = "sk-" + "a" * 32
        personal = "/" + "Users/" + "sample-person/" + "private/file"
        self.write("unsafe.py", 'API_KEY = "' + secret + '"\n' + personal + '\n')
        output = io.StringIO()
        with patch("sys.argv", ["check", "--root", str(self.root)]), contextlib.redirect_stdout(output):
            self.assertEqual(privacy.main(), 1)
        self.assertNotIn(secret, output.getvalue())
        self.assertNotIn(personal, output.getvalue())
        self.assertIn("unsafe.py:1: provider-token", output.getvalue())
        self.assertIn("unsafe.py:2: personal-path", output.getvalue())

    def test_notice_email_exception_does_not_exempt_secrets(self):
        email = "author" + "@" + "upstream.org"
        self.write("LICENSE", email + '\nAPI_KEY="' + "sk-" + "b" * 32 + '"\n')
        self.write("README.md", email)
        findings = privacy.scan(self.root)
        self.assertNotIn(("LICENSE", 1, "non-example-email"), findings)
        self.assertIn(("README.md", 1, "non-example-email"), findings)
        self.assertIn(("LICENSE", 2, "provider-token"), findings)

    def test_private_key_and_short_literal_are_detected(self):
        header = "-----BEGIN " + "PRIVATE KEY-----"
        self.assertIn("private-key", privacy.find_line_categories(header))
        self.assertIn("credential-literal", privacy.find_line_categories('password="' + 's3cr3t' + '"'))
        self.assertIn("provider-token", privacy.find_line_categories("AKIA" + "A" * 16))

    def test_synthetic_config_and_bootstrap_sources_are_allowed(self):
        self.write(".env.example", 'TOKEN="replace_with_generated_value"\n')
        self.write("database/bootstrap/001-schema.sql", "CREATE TABLE sample (id integer);\n")
        self.write("test.py", 'TOKEN="synthetic-import-test-token-123456"\n')
        self.assertEqual(privacy.scan(self.root), [])

    def test_unquoted_example_configuration_still_cannot_contain_credentials(self):
        value = "opaque" + "credential123456789"
        self.write(".env.example", "API_" + "KEY=" + value + "\n")
        self.assertEqual(privacy.scan(self.root), [(".env.example", 1, "credential-literal")])


class DocumentationLinksTest(CheckoutTest):
    def test_relative_links_images_and_references_with_encoded_spaces(self):
        self.write("docs/guide.md", "Guide")
        self.write("docs/screen shot.png", "image-fixture")
        self.write("README.md", '[Guide](docs/guide.md#intro)\n![Screen](docs/screen%20shot.png)\n[guide-ref]: <docs/guide.md>\n')
        self.assertEqual(links.scan(self.root), [])

    def test_missing_and_outside_targets_are_reported_at_correct_lines(self):
        self.write("README.md", '[Missing](docs/missing.md)\n[Outside](../outside.md)\n')
        self.assertEqual(links.scan(self.root), [
            ("README.md", 1, "missing-local-target"),
            ("README.md", 2, "target-outside-repository"),
        ])

    def test_code_examples_external_urls_and_fragment_links_are_ignored(self):
        self.write("README.md", '```md\n[Example](missing.md)\n```\n`[Example](missing.md)`\n[Web](https://example.com/)\n[Heading](#intro)\n')
        self.assertEqual(links.scan(self.root), [])


if __name__ == "__main__":
    unittest.main()
