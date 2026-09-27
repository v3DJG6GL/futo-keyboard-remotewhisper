#!/bin/sh
# Moves the whole fork onto a new upstream release tag.
#
#   fork/update-upstream.sh <new upstream tag>      e.g. fork/update-upstream.sh 0.1.31
#
# 1. fetches exactly that tag from the `upstream` remote (never all tags),
# 2. rebases every topic in fork/topics.txt order: @base topics onto the new tag,
#    stacked topics onto their (already rebased) parent topic,
# 3. rebuilds `main` (fork/rebuild-main.sh).
#
# Nothing is pushed. Review, build (fork/verify-build.sh), then push with
#   git push --force-with-lease forgejo main topic/...
#   git push forgejo refs/tags/<new tag>
set -eu
. "$(dirname "$0")/lib.sh"

new=${1:?usage: fork/update-upstream.sh <upstream tag>}
[ -z "$(git status --porcelain --untracked-files=no --ignore-submodules=all)" ] || die "working tree has uncommitted changes"

git config rerere.enabled true
git config rerere.autoUpdate true

git fetch --no-tags upstream "refs/tags/$new:refs/tags/$new"
old=$(upstream_base topic/fork-base)
[ "$old" != "$new" ] || die "topics are already based on $new"
echo "Moving topics from $old to $new"

start=$(git symbolic-ref --quiet --short HEAD || git rev-parse HEAD)
topic_list=$(topics)

# Build the full rebase plan up front, with every topic's current head resolved before
# anything is rewritten: stacked topics are rebased relative to their parent's old head.
plan=""
while read -r topic topic_base; do
    if ! branch_exists "$topic"; then
        echo "  skip   $topic (no such branch)"
        continue
    fi
    if [ "$topic_base" = "@base" ]; then
        plan="$plan
git rebase --quiet --onto $new $old $topic"
    else
        branch_exists "$topic_base" || die "$topic is stacked on $topic_base, which does not exist"
        plan="$plan
git rebase --quiet --onto $topic_base $(git rev-parse "$topic_base") $topic"
    fi
done <<EOF
$topic_list
EOF
plan=$(echo "$plan" | sed '/^$/d')

step_number=0
while read -r step; do
    step_number=$((step_number + 1))
    echo "  $step"
    if ! $step; then
        echo >&2
        echo "The rebase stopped on a conflict. Resolve it and run 'git rebase --continue'," >&2
        echo "then run the remaining steps yourself:" >&2
        echo "$plan" | sed -n "$((step_number + 1)),\$p" | sed 's/^/  /' >&2
        echo "  fork/rebuild-main.sh $new" >&2
        exit 1
    fi
done <<EOF
$plan
EOF

while read -r topic topic_base; do
    branch_exists "$topic" || continue
    if [ "$(git rev-list --count "$new..$topic")" -eq 0 ]; then
        echo "  $topic is now empty (merged upstream?) - consider removing it from fork/topics.txt"
    fi
done <<EOF
$topic_list
EOF

git checkout --quiet "$start" 2>/dev/null || true
"$FORK_ROOT/fork/rebuild-main.sh" "$new"

echo
echo "Done. Next: fork/verify-build.sh, then push:"
echo "  git push --force-with-lease forgejo main $(echo "$topic_list" | awk '{ printf "%s ", $1 }')"
echo "  git push forgejo refs/tags/$new"
