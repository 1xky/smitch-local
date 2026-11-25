#!/bin/sh

cd "$(dirname "$0")" || exit 1

if command -v python3 >/dev/null 2>&1; then
    exec python3 ./smitch.py "$@"
fi

printf '\nSmitch Local needs Python 3.10 or newer.\n'
printf 'Install it from https://www.python.org/downloads/macos/ and try again.\n\n'
read -r _
exit 1
