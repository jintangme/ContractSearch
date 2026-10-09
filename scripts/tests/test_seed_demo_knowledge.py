import contextlib
import importlib.util
import io
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'seed-demo-knowledge.py'
spec = importlib.util.spec_from_file_location('seed_demo_knowledge', SCRIPT)
seed_demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(seed_demo)


class FakeAgent:
    def __init__(self, invalid_source=False, orphan=False, stalled=False):
        self.calls = []
        self.invalid_source = invalid_source
        self.orphan = orphan
        self.stalled = stalled
        self.expected = list(seed_demo.expected_demo_sources().items())

    def post(self, payload):
        self.calls.append(payload)
        mode = payload['mode']
        if mode == 'VALIDATE':
            return {'mode': mode, 'validation': {'sources': 3, 'documents': 3, 'chunks': 7,
                    'searchRows': 7, 'orphanDocuments': int(self.orphan), 'orphanChunks': 0,
                    'missingChunkSearchRows': 0, 'duplicateSourceIdentities': 0}}
        start = payload.get('afterId', 0)
        selected = self.expected[start:start + 2]
        outcomes = [{'contractName': name, 'normalizedHash': digest, 'parserStatus': 'PARSED'}
                    for name, digest in selected]
        if self.invalid_source:
            outcomes[0]['normalizedHash'] = 'not-the-demo-source'
        return {'mode': mode, 'errorCount': 0, 'jobId': 'synthetic-test-job', 'outcomes': outcomes,
                'nextAfterId': 0 if self.stalled else start + len(selected),
                'hasMore': start + len(selected) < 3}


class SeedDemoKnowledgeTest(unittest.TestCase):
    def test_preflights_all_pages_reuses_job_and_validates_without_printing_it(self):
        agent = FakeAgent()
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            seed_demo.seed(agent)
        self.assertEqual([call['mode'] for call in agent.calls],
                         ['DRY_RUN', 'DRY_RUN', 'APPLY', 'APPLY', 'VALIDATE'])
        self.assertEqual(agent.calls[3]['jobId'], 'synthetic-test-job')
        self.assertEqual(agent.calls[3]['afterId'], 2)
        self.assertNotIn('synthetic-test-job', output.getvalue())
        self.assertIn('all zero', output.getvalue())

    def test_rejects_non_demo_source_before_any_apply(self):
        agent = FakeAgent(invalid_source=True)
        with self.assertRaises(seed_demo.SeedError):
            seed_demo.seed(agent)
        self.assertEqual([call['mode'] for call in agent.calls], ['DRY_RUN'])

    def test_stalled_pagination_cannot_loop_or_write(self):
        agent = FakeAgent(stalled=True)
        with self.assertRaises(seed_demo.SeedError):
            seed_demo.seed(agent)
        self.assertEqual(len(agent.calls), 1)

    def test_orphan_validation_fails(self):
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(seed_demo.SeedError):
            seed_demo.seed(FakeAgent(orphan=True))

    def test_rejects_remote_credential_and_path_urls(self):
        for url in ['https://example.invalid', 'http://127.0.0.1@evil.invalid',
                    'http://name:secret@localhost:18082', 'http://localhost:18082/api',
                    'http://localhost:18082/?token=secret']:
            with self.subTest(url=url), self.assertRaises(seed_demo.SeedError) as raised:
                seed_demo.local_base_url(url)
            self.assertNotIn(url, str(raised.exception))
        self.assertEqual(seed_demo.local_base_url('http://127.0.0.1:18082/'), 'http://127.0.0.1:18082')

    def test_requires_internal_token(self):
        with self.assertRaises(seed_demo.SeedError):
            seed_demo.Client('http://127.0.0.1:18082', '')


if __name__ == '__main__':
    unittest.main()
