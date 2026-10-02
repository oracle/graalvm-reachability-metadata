# FS-library-update-tested-version-split: Library-update tested-version split


A `library-update-request` entry often lists several tested versions of the same
library at once (for example `["1.1", "1.2", "1.3"]`). When a coverage-improvement
run regenerates the JVM tests for such an entry, the new tests can pass on the
entry's own version yet stop compiling or running against a *later* tested
version. Forge must catch that break before the branch becomes PR-eligible and
split the entry, so the PR keeps the regenerated progress for the versions that
still pass while the repository keeps its existing support for the rest.

**Version sweep.** Before publication (§FS-local-ci-equivalent-verification),
Forge sweeps the changed coordinate once per tested version. It runs `test` —
the JVM and the native tests — for the changed coordinate, walking the entry's
`tested-versions` in order with `GVM_TCK_LV` set to each version, and stops at
the first version that fails. The sweep is narrower than full CI — it runs one
coordinate, not the changed-metadata matrix — because it only needs to find the
first tested version on which the regenerated suite no longer works.

**Progress output.** Forge must report the sweep on the CLI: the changed
coordinate, how many versions it will check, each version as it starts, the log
path for that version, and whether the version passed or failed. When every
version passes, the output must say plainly that no split is needed.

**Outcome.** If the *first* version fails there is no passing prefix to keep, so
Forge fails publication instead of splitting. If a *later* version fails, Forge
splits the index entry at that first failing version into two entries:

| | `metadata-version` | `tested-versions` | `latest` | contents |
|---|---|---|---|---|
| **Current entry** | unchanged | the passing prefix | kept unless it moves to the successor | the regenerated metadata and tests from this PR |
| **Successor entry** | the first failing version | the failing version and every later one | inherited when the split entry had `latest: true` | baseline metadata and tests copied from the PR base commit |

**Successor contents.** The successor entry must preserve the repository's
pre-generation support for the failing range. Forge copies the metadata and test
directories from the PR base commit entry that originally covered the failing
version — using that entry's `metadata-version` and `test-version` when present —
into `metadata/<group>/<artifact>/<failing-version>` and
`tests/src/<group>/<artifact>/<failing-version>`. The copied test directory's
`gradle.properties` names the failing version as its library version and
metadata directory, as every owned test directory does, so the copy reads as the
successor's own project and not as a stale duplicate of its source. The PR then
ships the new generated progress for the passing prefix and keeps baseline
support for the successor range. Forge regenerates library stats for the failing version after
creating the successor entry and publishes them under
`stats/<group>/<artifact>/<failing-version>`.

**Follow-up issue.** On every split, Forge also opens a `library-update-request`
issue for the successor metadata version and holds it in `In Progress` so the
queue cannot claim it early. The PR references this issue but does not close it,
through the `Refs:` line and `Forge-Unblocks-Issue:` trailer of §AR-pr-body.
Once the PR merges, Forge releases the issue — clearing its assignees and moving
its project status to `Todo` — so the successor update enters normal processing.

## 1. Test-version consumers

A regenerated test directory is not only its target entry's. Every other entry
in the artifact's `index.json` whose `test-version` names that directory runs
the same suite, and CI tests each of them when the suite changes. A sweep that
checks only the target entry's own `tested-versions` therefore passes locally
and fails in CI whenever a consumer's versions no longer fit the new suite.
Forge extends the version sweep to those consumers before publication.

### 1.1 Consumer sweep

After the target entry's sweep, Forge collects the consumers — every other entry
whose `test-version` equals the regenerated directory's version — ordered by
`metadata-version`. For each consumer it runs `test` once per tested version,
resolved as CI resolves them: the coordinate is
`<group>:<artifact>:<consumer metadata-version>` and `GVM_TCK_LV` is the tested
version. The sweep stops at the first failure and reports its progress as the
version sweep does. When every consumer passes, nothing changes.

### 1.2 Consumer split

When a consumer `F` fails, Forge splits the consumers at `F`:

- it creates `tests/src/<group>/<artifact>/<F>/` from the PR base commit's copy
  of the shared suite, with `gradle.properties` naming `F` as the successor
  contents above do;
- it removes `test-version` from `F`'s entry, so `F` runs its own directory;
- it points every later consumer at that directory with `test-version: "<F>"`;
- it leaves the earlier, passing consumers on the regenerated suite;
- it leaves every consumer's metadata and stats unchanged.

Forge then reruns the consumer sweep for `F` and the re-pointed consumers on the
baseline suite. That suite is the support the repository already ships, so a
failure there is not the regenerated suite's to absorb, and it fails
publication. On success Forge opens the follow-up `library-update-request` issue
for `<group>:<artifact>:<F>` exactly as the tested-version split does, and the
PR references it the same way.

For a suite at `4.17.0` shared by `4.20.0` and `4.23.0`, where `4.20.0` fails:

| Entry | Before | After |
|---|---|---|
| `4.17.0` | own directory | own directory, regenerated suite |
| `4.20.0` | `test-version: "4.17.0"` | own directory, base-commit `4.17.0` suite |
| `4.23.0` | `test-version: "4.17.0"` | `test-version: "4.20.0"` |

## 2. Native failures

A regenerated suite can pass on the JVM for a version and still fail its native
tests, because it exercises dynamic access that version's metadata does not
register. The sweeps therefore run the native tests as well, and a native
failure splits exactly as a JVM failure does: the failing version and every
later one leave the regenerated suite and keep the support the repository
already ships.

Forge does not repair the failing version's metadata during a sweep. The repair
would change another entry's metadata inside a contribution that targets one
coordinate, and the native test verification gate
(§FS-native-test-verification-gate) may rewrite the suite as its last resort,
which would invalidate every version already swept. The follow-up issue the
split opens brings that version its own improvement run, where the gate belongs.
