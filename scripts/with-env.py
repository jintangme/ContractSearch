#!/usr/bin/env python3
"""Start one local service with the root .env loaded into its process environment."""
import argparse
import os
from pathlib import Path
from project_env import read_env

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--env-file', type=Path, default=Path(__file__).resolve().parents[1] / '.env')
parser.add_argument('command', nargs=argparse.REMAINDER)
args = parser.parse_args()
if not args.command:
    parser.error('Provide a command, e.g. java -jar backend/cloud-service/target/cloud-service-1.0-SNAPSHOT.jar')
if not args.env_file.is_file():
    parser.error('Create private configuration with scripts/configure-demo.py first')
environment = {**os.environ, **read_env(args.env_file)}
os.execvpe(args.command[0], args.command, environment)
