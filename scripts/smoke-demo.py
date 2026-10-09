#!/usr/bin/env python3
"""Exercise real basic-mode APIs using a synthetic account; no model calls."""
import argparse
import json
import secrets
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--wait', type=int, default=180)
    parser.add_argument('--runtime-base-url', help='Host development: http://127.0.0.1:19090')
    args = parser.parse_args()
    base = args.base_url.rstrip('/')
    token = ''

    def request(path, payload=None, authenticated=False):
        headers = {'Content-Type': 'application/json'}
        if authenticated:
            headers['Authorization'] = f'Bearer {token}'
        url = (args.runtime_base_url.rstrip('/') + path.removeprefix('/runtime-api')) if args.runtime_base_url and path.startswith('/runtime-api/') else base + path
        req = Request(url, data=json.dumps(payload).encode() if payload is not None else None, headers=headers)
        with urlopen(req, timeout=30) as response:
            return json.load(response)

    deadline = time.monotonic() + args.wait
    while True:
        try:
            groups = request('/api/groups')
            assert groups.get('status') is True and groups.get('data'), 'Demo groups are missing'
            break
        except (HTTPError, URLError, TimeoutError, AssertionError, json.JSONDecodeError):
            if time.monotonic() >= deadline:
                raise RuntimeError('Demo did not become ready; inspect local container logs') from None
            time.sleep(2)
    print('PASS database bootstrap and browse API')

    username = 'reviewer_' + secrets.token_hex(5)
    password = secrets.token_urlsafe(24)
    account = {'username': username, 'password': password, 'nickname': 'Demo reviewer', 'email': username + '@example.com'}
    assert request('/api/auth/register', account).get('status') is True, 'Registration failed'
    login = request('/api/auth/login', {'username': username, 'password': password})
    assert login.get('status') is True, 'Login failed'
    token = login['data']['token']
    print('PASS synthetic account registration and login (credentials omitted)')

    result = request('/api/search', {'input': 'owner', 'searchMode': 'code', 'limit': 5}, True)
    assert result.get('status') is True and result.get('data'), 'BM25 returned no demo contract'
    contract = request('/api/contracts/' + str(result['data'][0]['id']), authenticated=True)
    assert contract.get('status') is True, 'Contract detail failed'
    print('PASS BM25 code search and contract detail')

    source = '// SPDX-License-Identifier: UNLICENSED\npragma solidity ^0.8.20; contract DemoCounter { uint256 public value; function set(uint256 next) public { value = next; } }'
    compilation = request('/runtime-api/api/runtime/compile', {'sourceCode': source, 'fileName': 'DemoCounter.sol'})
    assert compilation.get('success') is True, 'Runtime compilation failed'
    print('PASS real Solidity compilation')

    try:
        request('/api/contracts/process', {'filePath': '/not-an-import.parquet'})
        raise AssertionError('Internal import endpoint must not be public')
    except HTTPError as error:
        assert error.code in (401, 403, 404), 'Unexpected import rejection'
    print('PASS internal import endpoint is unavailable through the gateway')
    print('Basic demo verified. No external model API was called.')


if __name__ == '__main__':
    main()
