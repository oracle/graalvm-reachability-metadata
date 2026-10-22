# ROADMAP-forge-implementation: Forge implementation roadmap

This roadmap lists the active implementation gaps to close in Forge against the
functional spec (§FS-forge-functional-spec), ordered by delivery priority. It
serves the overall Forge direction in §GOAL-forge-direction. Only open work is
listed: an item is deleted from this file once it ships, and the behavior it
introduced lives in the spec, workflow, or architecture declaration it cites.

1. Publication descriptor off the tree (§ROADMAP-forge-descriptor-off-tree).

# ROADMAP-forge-descriptor-off-tree: Publication descriptor off the tree

Priority: first (part of §ROADMAP-forge-implementation).

The publication descriptor is a build artifact of one run, not repository
content, but today it is committed at
`stats/<group>/<artifact>/<version>/forge-publication.json`, appears in the pull
request diff, and lands on `master` when the PR merges
(§AR-publication-descriptor). The repository has already had to absorb that:
the stats schema validator was widened to accept the file. Every merged Forge PR
leaves one behind, in a directory that otherwise holds derived per-version
metrics.

This item makes the descriptor exist for exactly as long as it is needed — from
the push that certifies a branch until the pull request is open — and never
enter a tree.

The constraint that shapes the design: an unprivileged local run hands data to
CI only through a pushed ref, and GitHub fires `push` workflows for
`refs/heads/*` and `refs/tags/*` only, so a side ref such as `refs/forge/*`
would be pushed and silently ignored. The descriptor therefore travels as an
annotated tag rather than as a commit:

- Local finalization pushes the verified branch, then an annotated tag
  `forge-publication/<publication-id>` pointing at the verified head commit,
  carrying the descriptor JSON as its message. The branch itself carries only
  generated work.
- Branch Ready triggers on that tag, validates the descriptor against its own
  copy of the schema from the default branch, and hands the commit on
  (§AR-actions-publication). The tag *is* the SHA binding: it names the commit
  it certifies, which removes the current awkwardness that the head SHA cannot
  be a descriptor field because a commit cannot contain its own object ID.
- The publisher reads the descriptor from the tag, opens the pull request
  against `descriptor.branch`, and deletes the tag once the PR exists, so the
  tag namespace does not accumulate one entry per publication.

Everything the descriptor is for survives unchanged: it is still data and never
GitHub instructions, still schema-validated by trusted default-branch code,
still bound to one exact commit, and still the only channel from local
verification to the publisher.

Retries need the tag to be force-updatable, and a re-pushed tag re-triggers
publication; the existing `Forge-Publication-ID` idempotency check absorbs that
by resolving to the pull request already opened for the identity. If a
descriptor ever outgrows a tag message, the fallback is a lightweight tag
pointing at a blob.

Acceptance: a merged Forge pull request adds no `forge-publication.json`
anywhere, and `stats/` contains only derived metrics; the pull request diff
contains only generated work; the stats schema validator no longer needs a
descriptor exemption; and a publication whose tag is re-pushed opens no second
pull request.
