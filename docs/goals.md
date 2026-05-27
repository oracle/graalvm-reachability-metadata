# GOAL-tested-metadata: Every metadata file is justified by a test

Every metadata file the repository ships should be justified by a corresponding
test that exhibits the dynamic access the metadata registers. Metadata is never
hand-edited into existence and then trusted; it is published only after a test
that would fail without it passes the harness gates, so the additive contract is
demonstrated rather than asserted.
§GRUND-repository-motivation

# GOAL-broad-version-coverage: As many tested versions as practical

Supported artifacts should have as many tested library versions as practical, so
consumers can rely on metadata across current and historical versions of the
same artifact. The repository should track upstream releases and record each new
version that passes the full test matrix, growing version coverage without a
human watching Maven Central.
§GRUND-repository-motivation

# GOAL-fresh-metadata: Keep metadata fresh through periodic releases

The repository should keep the metadata available to consumers fresh by
releasing on a regular cadence rather than letting verified coverage sit
unreleased. Forge automation (§forge/GOAL-forge-direction) can now add support
for many library versions in a short time span, so newly verified metadata
accumulates quickly; periodic releases must flow on schedule to deliver that
coverage to native-build-tools users promptly, keeping the released bundle close
to the repository's actual tested surface.
§GRUND-repository-motivation

# GOAL-protect-shipped-metadata: Grow coverage without regressions

Version and library coverage should grow without weakening already-supported
libraries, removing tested metadata, or breaking the repository's additive
metadata contract. New work — human or automated — must preserve the current
state of shipped metadata and its dynamic-access coverage, so additions never
become silent regressions.
§GRUND-repository-motivation
