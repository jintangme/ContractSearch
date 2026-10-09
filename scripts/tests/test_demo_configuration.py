import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('project_env', SCRIPTS / 'project_env.py')
environment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(environment)


class DemoConfigurationTests(unittest.TestCase):
    def test_generator_is_private_and_never_overwrites_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / '.env'
            command = [sys.executable, str(SCRIPTS / 'configure-demo.py'), '--output', str(target)]
            first = subprocess.run(command, capture_output=True, text=True, check=True)
            original = target.read_bytes()
            values = environment.read_env(target)
            self.assertEqual(target.stat().st_mode & 0o777, 0o600)
            self.assertNotEqual(values['JWT_SECRET'], values['AGENT_INTERNAL_TOKEN'])
            self.assertEqual(values['POSTGRESQL_DB_PASSWORD'], values['AGENT_DB_PASSWORD'])
            self.assertNotIn(values['JWT_SECRET'], first.stdout + first.stderr)
            second = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(second.returncode, 0)
            self.assertEqual(original, target.read_bytes())

    def test_env_values_are_literal_and_not_shell_commands(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / '.env'
            marker = Path(directory) / 'must-not-exist'
            target.write_text(f"VALUE='$(touch {marker})'\nOTHER=literal#part\n")
            values = environment.read_env(target)
            self.assertEqual(values['VALUE'], f'$(touch {marker})')
            self.assertEqual(values['OTHER'], 'literal#part')
            self.assertFalse(marker.exists())


if __name__ == '__main__':
    unittest.main()
