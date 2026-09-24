# Shared helpers for the fork/ scripts. Source it, don't run it.

FORK_ROOT=$(git rev-parse --show-toplevel)       # repository being worked on (the current directory)
FORK_TOPICS="$FORK_ROOT/fork/topics.txt"

die() {
    echo "error: $*" >&2
    exit 1
}

# Prints "<branch> <base>" for every topic, in file order.
topics() {
    [ -f "$FORK_TOPICS" ] || die "missing $FORK_TOPICS (run from a branch that contains fork/)"
    sed -e 's/#.*//' "$FORK_TOPICS" | awk 'NF >= 2 { print $1, $2 }'
}

# Newest upstream release tag reachable from a ref (default HEAD).
upstream_base() {
    git describe --tags --abbrev=0 --match '[0-9]*' --exclude '*-rc*' "${1:-HEAD}"
}

branch_exists() {
    git show-ref --verify --quiet "refs/heads/$1"
}

# Fails if the branch is checked out in any worktree.
require_not_checked_out() {
    if git worktree list --porcelain | grep -qx "branch refs/heads/$1"; then
        die "branch $1 is checked out in a worktree; switch away from it first"
    fi
}
