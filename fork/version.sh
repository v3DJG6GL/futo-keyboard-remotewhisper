#!/bin/sh
# Prints VERSION_NAME and VERSION_CODE for HEAD, one KEY=VALUE per line.
#
#   base  = newest upstream release tag in HEAD's history (e.g. 0.1.30)
#   name  = <base>-rw.<N>                 when HEAD carries the tag v<base>-rw.<N>
#           <base>-rw.<N>-dev.<sha>       otherwise (N = next release number for this base)
#   code  = <first-parent commit count of base> * 100 + N
#
# The first-parent count of an upstream tag is upstream's own versionCode for that release,
# so codes grow with every upstream base and leave room for 99 fork releases per base.
set -eu

cd "$(dirname "$0")/.."

base=$(git describe --tags --abbrev=0 --match '[0-9]*' --exclude '*-rc*' HEAD)
base_code=$(git rev-list --first-parent --count "$base")

if tag=$(git describe --tags --exact-match --match "v${base}-rw.*" HEAD 2>/dev/null); then
    name=${tag#v}
    n=${name##*-rw.}
else
    last=$(git tag -l "v${base}-rw.*" --sort=-v:refname | head -n 1)
    if [ -n "$last" ]; then
        n=$(( ${last##*-rw.} + 1 ))
    else
        n=1
    fi
    name="${base}-rw.${n}-dev.$(git rev-parse --short HEAD)"
fi

if [ "$n" -lt 1 ] || [ "$n" -gt 99 ]; then
    echo "fork/version.sh: release number $n for base $base is out of range 1..99" >&2
    exit 1
fi

echo "VERSION_NAME=$name"
echo "VERSION_CODE=$(( base_code * 100 + n ))"
