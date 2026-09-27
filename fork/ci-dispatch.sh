#!/bin/sh
# Starts the Forgejo build workflow for a branch that carries no workflow files itself
# (the upstream-clean topic/dict-* branches). The workflow runs from main and builds <ref>.
#
#   FORGEJO_TOKEN=<token with write:repository> fork/ci-dispatch.sh <ref>
set -eu

ref=${1:?usage: FORGEJO_TOKEN=... fork/ci-dispatch.sh <branch, tag or commit>}
token=${FORGEJO_TOKEN:?set FORGEJO_TOKEN to a Forgejo access token}
repo=${FORGEJO_REPO:-https://forgejo.informethic.ch/api/v1/repos/v3djg6gl/futo-keyboard-remotewhisper}

curl -fsS -X POST \
    -H "Authorization: token $token" \
    -H 'Content-Type: application/json' \
    -d "{\"ref\":\"main\",\"inputs\":{\"ref\":\"$ref\"}}" \
    "$repo/actions/workflows/build.yml/dispatches"
echo "Dispatched build of $ref"
