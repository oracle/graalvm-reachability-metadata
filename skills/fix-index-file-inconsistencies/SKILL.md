---
name: fix-index-file-inconsistencies
description: Fix graalvm-reachability-metadata PRs where final validateIndexFiles against current master fails because metadata index.json tested versions are in the wrong metadata-version bucket, duplicated across buckets, or stale after another index-changing PR landed. Use during PR review when an index-changing PR must be repaired and pushed instead of labeled human-intervention.
---

# Fix Index File Inconsistencies

Use this skill while reviewing a PR that changes
`metadata/<group>/<artifact>/index.json` and fails final index validation on
current `master`.

The goal is to repair the PR branch, commit the fix, push it to the same PR,
and then continue the review. Do not label the PR `human-intervention` for
fixable index bucket inconsistencies.

## What To Fix

`validateIndexFiles` enforces that each non-`latest` metadata entry owns only
tested versions that belong before the next metadata entry. Common failures:

- A PR was valid when an older entry was `latest`, but another PR added a newer
  metadata entry first. The older entry is now non-`latest`, so the PR's added
  tested versions must move to a later bucket.
- A tested version appears in an older entry even though the same version has
  its own metadata entry.
- A tested version was appended to the closest textual entry instead of the
  compatible metadata-version range.

Fix only the affected `index.json` files unless the branch must first be
rebased onto `origin/master` so the correct target bucket exists locally.

## Reproduce The Final Candidate

From the PR review worktree:

```bash
PR_NUMBER=<pr>
git fetch --quiet origin +master:refs/remotes/origin/master
PR_HEAD="$(git rev-parse HEAD)"
TMP="$(mktemp -d)"
git worktree add --detach "$TMP" origin/master
git -C "$TMP" fetch --quiet origin "refs/pull/${PR_NUMBER}/head"
test "$(git -C "$TMP" rev-parse FETCH_HEAD)" = "$PR_HEAD"
git -C "$TMP" merge --no-commit --no-ff FETCH_HEAD
(cd "$TMP" && ./gradlew validateIndexFiles -Pcoordinates=all --stacktrace)
git worktree remove --force "$TMP"
```

Read the validation errors. They usually name the library coordinate and the
specific tested versions that exceed the next metadata-version boundary.

## Repair The PR Branch

The review worktree starts in detached HEAD. To push a fix, check out the PR
branch only after you have confirmed this is a fixable index inconsistency:

```bash
PR_NUMBER=<pr>
gh pr view "$PR_NUMBER" --json headRefName,headRepositoryOwner,isCrossRepository
gh pr checkout "$PR_NUMBER"
git fetch --quiet origin +master:refs/remotes/origin/master
git rebase origin/master
```

If the PR is from a fork and you cannot push to it, request changes with the
validation error and the exact index repair needed. Do not add
`human-intervention`.

Edit the affected `index.json` files:

- Move tested versions from the older metadata entry into the correct later
  entry when the validator says they are greater than or equal to the next
  metadata-version boundary.
- Remove a tested version from an older entry when that version already has its
  own metadata entry.
- Preserve URL fields, `allowed-packages`, `requires`, `latest`, and
  `metadata-version` unless the validator error directly proves one is wrong.
- Preserve the file's existing JSON formatting and version ordering style.

Validate the repair:

```bash
./gradlew validateIndexFiles -Pcoordinates=all --stacktrace
```

Then commit and push:

```bash
git diff
git add metadata/<group>/<artifact>/index.json
git commit -m "Fix index version buckets"
git push --force-with-lease
```

After pushing, submit the GitHub review. If the fix is complete and no other
blocking issues remain, approve with a short note that the index bucket repair
was pushed and full index validation now passes.

## Examples

### Stale latest bucket

An older Jetty entry was once `latest`, so a PR appended `9.4.1` through
`9.4.7` there. After another PR added a Jetty `9.4.8` entry, the old entry was
no longer `latest`, and the validator rejected the `9.4.x` tested versions
under the `9.3.x` metadata bucket.

Fix: move `9.4.1` through `9.4.7` from the `9.3.19.v20170502` entry into the
`9.4.0.v20180619` entry, leaving the `9.3.x` tested versions in the older
entry.

### Duplicate tested version

A Camel entry for `4.19.0` contained:

```json
"tested-versions" : [
  "4.19.0",
  "4.20.0"
]
```

but `4.20.0` already had its own metadata entry.

Fix: remove `4.20.0` from the `4.19.0` entry. Do not delete the `4.20.0`
metadata entry.

## Review Decision

- If the index repair validates and the rest of the PR is clean, approve.
- If the index repair exposes unrelated contamination, request changes for the
  unrelated issue.
- If the branch cannot be pushed, request changes with the exact repair
  instructions.
