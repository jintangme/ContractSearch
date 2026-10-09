#!/usr/bin/env python3
"""Create local demo configuration without printing or overwriting secrets."""
import argparse
import os
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / '.env')
    args = parser.parse_args()
    database_password = secrets.token_hex(32)
    replacements = {
        'JWT_SECRET': secrets.token_hex(32),
        'AGENT_INTERNAL_TOKEN': secrets.token_hex(32),
        'POSTGRESQL_DB_PASSWORD': database_password,
        'AGENT_DB_PASSWORD': database_password,
    }
    lines = []
    for line in (ROOT / '.env.example').read_text().splitlines():
        key = line.partition('=')[0]
        lines.append(f'{key}={replacements[key]}' if key in replacements else line)
    try:
        fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        parser.exit(1, 'Configuration already exists; preserved without changes.\n')
    with os.fdopen(fd, 'w') as target:
        target.write('\n'.join(lines) + '\n')
    print('Created private configuration (0600). No credentials were printed.')
    print('Start basic mode: docker compose up --build -d')


if __name__ == '__main__':
    main()
