#!/bin/sh
# Initializes all submodules of the repository in the current directory, retrying with backoff.
#
# The swipe model submodule (java/assets/futo-swipe) lives on huggingface.co, which rate-limits
# anonymous clones (HTTP 429). Set HF_TOKEN to clone it authenticated. The submodule stores its
# model files with git-lfs, so git-lfs must be installed or the checkout contains pointer files.
set -eu

command -v git-lfs >/dev/null 2>&1 || { echo "error: git-lfs is not installed" >&2; exit 1; }
git lfs install --local >/dev/null

if [ -n "${HF_TOKEN:-}" ]; then
    git config --global url."https://hf:${HF_TOKEN}@huggingface.co/".insteadOf "https://huggingface.co/"
    echo "Using authenticated huggingface.co access."
else
    echo "warning: HF_TOKEN not set; cloning the huggingface submodule anonymously (rate-limit prone)"
fi

tries=6
i=1
while [ "$i" -le "$tries" ]; do
    if git submodule update --init --recursive --force --jobs 4; then
        echo "Submodules initialized."
        exit 0
    fi
    if [ "$i" -lt "$tries" ]; then
        wait=$((i * 30))
        echo "warning: submodule init attempt $i/$tries failed; retrying in ${wait}s"
        sleep "$wait"
    fi
    i=$((i + 1))
done
echo "error: submodule init failed after $tries attempts" >&2
exit 1
