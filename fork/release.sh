#!/bin/sh
# Tags the current `main` as the next fork release and pushes the tag to Forgejo,
# which triggers the release workflow.
#
#   fork/release.sh            next release number for the current upstream base
#   fork/release.sh <N>        explicit release number
#
# Tag format: v<upstream base>-rw.<N>, e.g. v0.1.30-rw.3 (versionName 0.1.30-rw.3).
set -eu
. "$(dirname "$0")/lib.sh"

remote=${FORK_REMOTE:-forgejo}

[ "$(git symbolic-ref --quiet --short HEAD)" = "main" ] || die "check out main first"
[ -z "$(git status --porcelain --untracked-files=no --ignore-submodules=all)" ] || die "working tree has uncommitted changes"
git fetch --quiet "$remote" main
[ "$(git rev-parse HEAD)" = "$(git rev-parse "$remote/main")" ] || die "main differs from $remote/main; push it first"

base=$(upstream_base HEAD)
base_code=$(git rev-list --first-parent --count "$base")
if [ $# -ge 1 ]; then
    n=$1
else
    last=$(git tag -l "v${base}-rw.*" --sort=-v:refname | head -n 1)
    n=1
    [ -z "$last" ] || n=$(( ${last##*-rw.} + 1 ))
fi
tag="v${base}-rw.${n}"
code=$(( base_code * 100 + n ))
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && die "tag $tag already exists"

# versionCode must grow, or installed apps refuse the update.
for existing in $(git tag -l 'v*-rw.*'); do
    existing_base=${existing#v}
    existing_base=${existing_base%-rw.*}
    existing_code=$(( $(git rev-list --first-parent --count "$existing_base") * 100 + ${existing##*-rw.} ))
    [ "$code" -gt "$existing_code" ] || die "$tag (versionCode $code) is not newer than $existing ($existing_code)"
done

echo "Releasing $tag (versionName ${tag#v}, versionCode $code) from $(git rev-parse --short HEAD)"
printf 'Proceed? [y/N] '
read -r answer
[ "$answer" = "y" ] || die "aborted"

git tag -a "$tag" -m "Release ${tag#v} (based on upstream $base)"
git push "$remote" "refs/tags/$tag"
echo "Pushed $tag; the release workflow on $remote builds and publishes the APK."
