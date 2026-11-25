#!/bin/sh

cd "$(dirname "$0")" || exit 1

if ! command -v python3 >/dev/null 2>&1; then
    printf 'Smitch Local needs Python 3.10 or newer.\n' >&2
    exit 1
fi

exec python3 ./smitch.py "$@"
