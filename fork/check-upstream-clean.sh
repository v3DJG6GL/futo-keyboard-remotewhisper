#!/bin/sh
# Checks that a topic branch contains nothing fork-specific, so it can be offered upstream.
#
#   fork/check-upstream-clean.sh <topic branch> [<base>]    (default base: its upstream tag)
#
# Fails when the topic touches fork-owned paths, changes a submodule pointer or adds the
# INTERNET permission (upstream's own CI rejects stable builds that request it).
set -eu
. "$(dirname "$0")/lib.sh"

topic=${1:?usage: fork/check-upstream-clean.sh <topic branch> [<base>]}
base=${2:-$(upstream_base "$topic")}
status=0

fork_paths=$(git diff --name-only "$base" "$topic" -- fork java/remotewhisper .forgejo FORK.md)
if [ -n "$fork_paths" ]; then
    echo "fork-owned paths changed:"; echo "$fork_paths" | sed 's/^/  /'
    status=1
fi

gitlinks=$(git diff --raw "$base" "$topic" | awk '$1 ~ /160000/ || $2 ~ /160000/ { print $NF }')
if [ -n "$gitlinks" ]; then
    echo "submodule pointers changed:"; echo "$gitlinks" | sed 's/^/  /'
    status=1
fi

if git diff "$base" "$topic" | grep -q '^+.*android\.permission\.INTERNET'; then
    echo "adds android.permission.INTERNET"
    status=1
fi

if [ "$status" -eq 0 ]; then
    echo "$topic is upstream-clean against $base ($(git rev-list --count "$base..$topic") commits)"
fi
exit $status
