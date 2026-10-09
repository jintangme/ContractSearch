#!/usr/bin/env python3
"""Import only the repository's original demo contracts into the local knowledge corpus.

Run with: python3 scripts/with-env.py python3 scripts/seed-demo-knowledge.py
The Agent knowledge API and migrations must be enabled. No model is called.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
ENDPOINT = '/internal/agent/v1/knowledge/ingestions'
MAX_RESPONSE_BYTES = 1024 * 1024
MAX_BATCHES = 5
BATCH_SIZE = 2


class SeedError(Exception):
    """A safe diagnostic that never contains server response bodies or secrets."""


class NoRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def local_base_url(value):
    try:
        parsed = urlsplit(value)
        if (parsed.scheme != 'http' or parsed.hostname not in {'127.0.0.1', 'localhost', '::1'}
                or parsed.username is not None or parsed.password is not None
                or parsed.path not in {'', '/'} or parsed.query or parsed.fragment):
            raise ValueError()
        if parsed.port is not None and not 1 <= parsed.port <= 65535:
            raise ValueError()
    except ValueError:
        raise SeedError('Use a loopback HTTP Agent base URL without credentials, path, or query.') from None
    return value.rstrip('/')


def expected_demo_sources():
    sql = (ROOT / 'database/bootstrap/002-demo.sql').read_text(encoding='utf-8')
    fixtures = re.findall(r"\('demo:[^']+', '([^']+)', \$sol\$([\s\S]*?)\$sol\$", sql)
    if len(fixtures) != 3:
        raise SeedError('Expected the three original contracts in database/bootstrap/002-demo.sql.')
    expected = {}
    # Match SoliditySourceRouter.normalize and its single-file manifest hash.
    for name, source in fixtures:
        lines = source.replace('\r\n', '\n').replace('\r', '\n').split('\n')
        while lines and not lines[-1].strip():
            lines.pop()
        normalized = ''.join(line.rstrip(' \t') + '\n' for line in lines)
        content_hash = hashlib.sha256(normalized.encode('utf-8')).hexdigest()
        manifest = f'Contract.sol\0{content_hash}\n'
        expected[name] = hashlib.sha256(manifest.encode('utf-8')).hexdigest()
    return expected


class Client:
    def __init__(self, base_url, token, timeout=30):
        self.url = local_base_url(base_url) + ENDPOINT
        if len(token) < 24:
            raise SeedError('Set AGENT_INTERNAL_TOKEN to the same 24+ character token used by the Agent service.')
        self.token = token
        self.timeout = timeout
        # Internal credentials must never follow a redirect or an environment HTTP proxy.
        self.opener = build_opener(ProxyHandler({}), NoRedirects())

    def post(self, payload):
        request = Request(self.url, data=json.dumps(payload).encode('utf-8'), method='POST', headers={
            'Content-Type': 'application/json', 'Accept': 'application/json',
            'X-Agent-Internal-Token': self.token,
        })
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                body = response.read(MAX_RESPONSE_BYTES + 1)
                if len(body) > MAX_RESPONSE_BYTES:
                    raise SeedError('Agent response exceeded the expected size; import stopped.')
                result = json.loads(body)
        except HTTPError as error:
            status = error.code
            error.close()
            hints = {
                401: 'Check that the local Agent and this process use the same internal token.',
                404: 'Start the Agent with AGENT_KNOWLEDGE_ENABLED=true and apply its migrations.',
            }
            raise SeedError(f'Agent returned HTTP {status}. '
                            + hints.get(status, 'Inspect the local Agent logs; the response body was not printed.')) from None
        except (URLError, TimeoutError, OSError):
            raise SeedError('Cannot reach the local Agent within the timeout. Check its port and startup logs.') from None
        except (ValueError, UnicodeError):
            raise SeedError('Agent returned an invalid JSON response; no response content was printed.') from None
        if not isinstance(result, dict):
            raise SeedError('Agent response was not a JSON object.')
        return result


def number(data, key):
    value = data.get(key)
    if type(value) is not int or value < 0:
        raise SeedError('Agent response contains missing or invalid counters.')
    return value


def check_outcomes(result, expected, seen):
    outcomes = result.get('outcomes')
    if not isinstance(outcomes, list):
        raise SeedError('Agent response is missing contract outcomes.')
    for outcome in outcomes:
        if not isinstance(outcome, dict):
            raise SeedError('Agent response contains invalid contract outcomes.')
        name = outcome.get('contractName')
        if (name not in expected or outcome.get('normalizedHash') != expected[name]
                or outcome.get('parserStatus') != 'PARSED' or name in seen):
            raise SeedError('The database does not match the original demo fixture. Use a separate, unchanged demo database.')
        seen.add(name)


def walk(client, mode, expected):
    after_id = 0
    job_id = None
    seen = set()
    for _ in range(MAX_BATCHES):
        payload = {'mode': mode, 'afterId': after_id, 'limit': BATCH_SIZE, 'useAst': mode == 'APPLY'}
        if job_id is not None:
            payload['jobId'] = job_id
        result = client.post(payload)
        if result.get('mode') != mode or number(result, 'errorCount') != 0:
            raise SeedError('Knowledge ingestion reported errors. Check runtime availability and the local Agent logs.')
        check_outcomes(result, expected, seen)
        if mode == 'APPLY':
            returned_job = result.get('jobId')
            if not isinstance(returned_job, str) or not returned_job or (job_id and returned_job != job_id):
                raise SeedError('Agent returned an inconsistent ingestion checkpoint.')
            job_id = returned_job
        next_after = number(result, 'nextAfterId')
        has_more = result.get('hasMore')
        if type(has_more) is not bool:
            raise SeedError('Agent response is missing the pagination status.')
        if not has_more:
            if seen != set(expected):
                raise SeedError('The complete three-contract demo fixture was not found; import stopped.')
            return
        if next_after <= after_id:
            raise SeedError('Agent pagination did not advance; import stopped.')
        after_id = next_after
    raise SeedError('Demo ingestion exceeded its small batch limit; import stopped.')


def seed(client):
    expected = expected_demo_sources()
    # The ingestion API selects the whole catalog. Verify its contents before any write.
    walk(client, 'DRY_RUN', expected)
    print('Verified three original demo contracts. Importing knowledge chunks...')
    walk(client, 'APPLY', expected)
    result = client.post({'mode': 'VALIDATE'})
    validation = result.get('validation')
    if result.get('mode') != 'VALIDATE' or not isinstance(validation, dict):
        raise SeedError('Agent did not return knowledge validation results.')
    for key in ('orphanDocuments', 'orphanChunks', 'missingChunkSearchRows', 'duplicateSourceIdentities'):
        if number(validation, key) != 0:
            raise SeedError('Knowledge consistency checks failed. Inspect the local Agent database before retrying.')
    sources = number(validation, 'sources')
    documents = number(validation, 'documents')
    chunks = number(validation, 'chunks')
    search_rows = number(validation, 'searchRows')
    if sources < 3 or documents < 3 or chunks < 3 or search_rows != chunks:
        raise SeedError('Knowledge validation found incomplete demo sources or chunk search rows.')
    print(f'Knowledge ready: {sources} sources, {documents} documents, {chunks} indexed chunks.')
    print('Orphan documents/chunks, missing search rows, and duplicate identities: all zero. No model calls made.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:18082', help='Loopback Agent URL (default: %(default)s)')
    args = parser.parse_args()
    try:
        seed(Client(args.base_url, os.environ.get('AGENT_INTERNAL_TOKEN', '')))
    except SeedError as error:
        print(f'Demo knowledge setup failed: {error}', file=sys.stderr)
        return 1
    except OSError:
        print('Demo knowledge setup failed: unable to read the local demo fixture.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
