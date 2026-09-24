# Maintaining this fork

This repository is **FUTO Keyboard (Remote-Whisper)**, a modified version of
[FUTO Keyboard](https://github.com/futo-org/android-keyboard). It is kept as a small stack of
topic branches on top of upstream **release tags**, so taking a new upstream release is a
rebase, not a merge of two diverging histories.

- Primary repository: <https://forgejo.informethic.ch/v3djg6gl/futo-keyboard-remotewhisper>
- Mirror (upstream pull requests are opened from here): <https://github.com/v3DJG6GL/futo-keyboard-remotewhisper>

## Branches and tags

| Ref | What it is |
|---|---|
| upstream tags (`0.1.30`, `0.1.31`, …) | the base of every fork release |
| `topic/dict-*` | **upstream-clean** changes, based directly on the upstream tag; can be offered upstream as-is |
| `topic/fork-base` | fork plumbing: `fork/`, the `java/remotewhisper/` flavor overlay, README notice, this file |
| `topic/whisper-backend` | the remote Whisper transcription backend (stacked on `topic/fork-base`) |
| `topic/ci` | Forgejo Actions workflows (stacked on `topic/fork-base`) |
| `main` | **generated**: upstream tag + a `--no-ff` merge of every topic, in the order of `fork/topics.txt`; rebuilt and force-pushed after every change |
| `v<base>-rw.<N>` | fork releases, e.g. `v0.1.30-rw.3` → versionName `0.1.30-rw.3` |

Never commit directly on `main`: commit on the topic branch, then run `fork/rebuild-main.sh`.

## Where fork code lives

Everything fork-only lives in `fork/`, `java/remotewhisper/`, `.forgejo/` and this file.
Upstream files only carry small, insert-only hooks. Rules:

1. Put new code in new files. In upstream files, insert a few lines that call into the new
   code. Never re-indent, rename, reorder or delete upstream lines, not even unused imports.
2. Fork-only resources go to `java/remotewhisper/res/` and are prefixed `fork_`
   (strings, drawables, xml) so they can never collide with a future upstream resource.
3. Fork-only permissions, manifest entries and dependencies belong to the `remotewhisper`
   flavor (`java/remotewhisper/AndroidManifest.xml`, `remotewhisperImplementation` in
   `fork/fork.gradle`), never to `java/AndroidManifest.xml` or a shared module.
4. Changes that could be useful upstream go to their own `topic/<name>` branch based on the
   upstream tag and must pass `fork/check-upstream-clean.sh topic/<name>`.

Review what the fork changes in upstream files with:

    git diff <base tag> main -- . ':!fork' ':!java/remotewhisper' ':!.forgejo' ':!FORK.md'

### The `remotewhisper` flavor

`build.gradle` ends with `apply from: 'fork/fork.gradle'`, which adds the flavor dimension
`distribution` with a single flavor `remotewhisper`. It sets the package id
(`io.github.v3djg6gl.futo.keyboard.remotewhisper`), versionName and versionCode, and adds
the `java/remotewhisper/` source set. Variant names therefore gain a prefix:

| Upstream task | Fork task (the upstream name still works as an alias) |
|---|---|
| `assembleUnstableDebug` | `assembleRemotewhisperUnstableDebug` |
| `assembleStableRelease` | `assembleRemotewhisperStableRelease` |
| `testUnstableDebugUnitTest` | `testRemotewhisperUnstableDebugUnitTest` |

## Versions

`fork/version.sh` derives both numbers from git:

- versionName: `<base>-rw.<N>` on a release tag, `<base>-rw.<N>-dev.<sha>` otherwise.
- versionCode: `<first-parent commit count of the base tag> * 100 + N`. The first-parent
  count is upstream's own versionCode for that release, so codes always grow with the
  upstream base and leave room for 99 fork releases per base.

A versionCode must never decrease, or installed apps refuse the update. `fork/release.sh`
checks this before tagging. CI can override both values with `VERSION_NAME`/`VERSION_CODE`.

## Everyday tasks

### Change a topic

```sh
git switch topic/whisper-backend        # or any other topic
# edit, build, commit
fork/rebuild-main.sh                    # regenerate main
git push --force-with-lease forgejo topic/whisper-backend main
```

For stacked topics (`topic/whisper-backend`, `topic/ci`) that need a change in
`topic/fork-base`: commit on `topic/fork-base`, then
`git rebase --onto topic/fork-base <old fork-base head> topic/whisper-backend` (same for
`topic/ci`), then rebuild `main`.

### Add a topic

1. `git switch -c topic/<name> <base tag>` (or on top of `topic/fork-base` if it needs fork
   plumbing).
2. Add it to `fork/topics.txt` on `topic/fork-base` (upstream-clean topics before
   `topic/fork-base`).
3. `fork/rebuild-main.sh`.

### Take a new upstream release

```sh
fork/update-upstream.sh 0.1.31          # fetch the tag, rebase all topics, rebuild main
fork/verify-build.sh                    # build + check package id / versionCode / LFS files
git push --force-with-lease forgejo main topic/dict-recorrection-fix topic/dict-learned-words topic/fork-base topic/whisper-backend topic/ci
git push forgejo refs/tags/0.1.31
```

Only upstream **release** tags are fetched, one at a time (`remote.upstream.tagOpt` is
`--no-tags`); upstream carries old orphan `v0.1.2x` tags that must not end up here. Conflicts
appear only in hook lines; `git rerere` (enabled by the scripts) replays resolutions you have
already made.

### Release

```sh
git switch main
fork/release.sh                         # tags v<base>-rw.<next N> and pushes the tag
```

The tag push triggers `.forgejo/workflows/release.yml`, which builds the signed
`remotewhisperStableRelease` APK and publishes a Forgejo release.

### Offer a topic upstream

```sh
fork/check-upstream-clean.sh topic/dict-recorrection-fix
git fetch upstream master
git branch pr/dict-recorrection-fix topic/dict-recorrection-fix
git rebase --onto upstream/master <base tag> pr/dict-recorrection-fix
git push github pr/dict-recorrection-fix  # open the pull request from the GitHub mirror
```

Once upstream has merged it, the topic becomes empty on the next `fork/update-upstream.sh`;
remove it from `fork/topics.txt`.

## Setup for a fresh clone

```sh
sudo apt install git-lfs && git lfs install   # java/assets/futo-swipe is stored with LFS
git clone --recurse-submodules https://forgejo.informethic.ch/v3djg6gl/futo-keyboard-remotewhisper.git
cd futo-keyboard-remotewhisper
git remote add upstream https://github.com/futo-org/android-keyboard.git
git config remote.upstream.tagOpt --no-tags
git remote add github https://github.com/v3DJG6GL/futo-keyboard-remotewhisper.git
git config rerere.enabled true
git fetch forgejo 'refs/heads/topic/*:refs/heads/topic/*'
```

## CI

`.forgejo/workflows/build.yml` builds `main` and `topic/ci` on every push. Forgejo only runs
workflows that exist in the pushed branch, so the other topics are built as part of `main`.
Upstream-clean topics carry no workflow files, so pushing them triggers nothing; build one
with a manual run of the workflow on `main` (Actions tab, `ref` = the topic) or
`FORGEJO_TOKEN=… fork/ci-dispatch.sh topic/<name>`. `.forgejo/workflows/release.yml` runs
on `v*-rw.*` tags.

### Secrets (Forgejo → repository settings → Actions → Secrets)

| Secret | Used for |
|---|---|
| `KEYSTORE_B64` | base64 of the release keystore. Must be the key that signed every earlier release, or users cannot update |
| `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEYSTORE_ALIAS` | keystore credentials |
| `RELEASE_TOKEN` | token with repository write access, to publish releases |
| `HF_TOKEN` | optional; avoids huggingface rate limits when cloning the swipe model submodule |
