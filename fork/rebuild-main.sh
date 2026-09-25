#!/bin/sh
# Rebuilds `main` from scratch: <upstream tag> + a --no-ff merge of every topic in
# fork/topics.txt order. Missing topic branches are skipped with a warning.
#
#   fork/rebuild-main.sh [<upstream tag>]      (default: the base of topic/fork-base)
#
# Runs in a temporary worktree, so your current checkout is untouched. Conflicts are
# resolved automatically where git rerere has seen them before; otherwise the script
# stops and leaves the worktree for you to resolve (then rerun the script, which reuses
# your recorded resolution).
set -eu
. "$(dirname "$0")/lib.sh"

base=${1:-$(upstream_base topic/fork-base)}
git rev-parse -q --verify "refs/tags/$base" >/dev/null || die "unknown tag $base"
require_not_checked_out main

git config rerere.enabled true
git config rerere.autoUpdate true

topic_list=$(topics)
work=$(mktemp -d "${TMPDIR:-/tmp}/fork-rebuild-main.XXXXXX")
rmdir "$work"
git worktree add --quiet --detach "$work" "$base"

echo "Rebuilding main on $base in $work"
while read -r topic topic_base; do
    if ! branch_exists "$topic"; then
        echo "  skip   $topic (no such branch)"
        continue
    fi
    if ! git -C "$work" merge --no-ff --no-edit -m "Merge $topic" "$topic" >/dev/null 2>&1; then
        if [ -n "$(git -C "$work" diff --name-only --diff-filter=U)" ]; then
            echo "  CONFLICT merging $topic. Resolve it (rerere records the resolution), then:" >&2
            echo "    cd $work && git add -A && git commit --no-edit && cd -" >&2
            echo "    git worktree remove --force $work && fork/rebuild-main.sh $base" >&2
            exit 1
        fi
        # rerere resolved every conflict; record the merge.
        git -C "$work" add -A
        git -C "$work" commit --no-edit --quiet
    fi
    echo "  merged $topic"
done <<EOF
$topic_list
EOF

git branch -f main "$(git -C "$work" rev-parse HEAD)"
git worktree remove --force "$work"
echo "main is now $(git rev-parse --short main): $base + $(git rev-list --count --merges "$base"..main) topic merges"
