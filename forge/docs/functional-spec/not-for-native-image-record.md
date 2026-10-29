# FS-not-for-native-image-record: Not-for-native-image record in prepare

A `library-new-request` can name an artifact that is not a JVM library at all:
a Kotlin/JS or Kotlin/Native `klib`, an Android `aar`, a POM-only aggregate, or
a starter jar with no class files. No test or metadata can exist for it, so a
run that tried would fail in its scaffold, release the claim, and meet the same
issue on the next cycle, and the next reporter would ask again. The repository
answers such an artifact once, with a `not-for-native-image` index entry
(§root/FS-metadata.1), and triage closes every later request for it
(§root/AR-triage-new-issues). `prepare` writes that entry before anything is
scaffolded, so the record is the run's outcome rather than a maintainer's chore.

## 1. The evidence

Only `library-new-request` issues whose artifact has no `index.json` on the
pinned base are asked; every other label names an artifact the index already
supports. The verdict rests only on the artifact's own published files on Maven
Central, and requires one of:

1. the POM's packaging is `klib` or `aar`;
2. the POM's packaging is `pom` and no jar is published; or
3. the published jar holds no JVM class file besides `module-info.class`.

The coordinate's name is never evidence: a `-js` or platform suffix also names
JVM jars, such as compiler plugins. Nor is a POM that declares a relocation,
even with packaging `pom` and no jar: it says the coordinate moved, and the
record, which covers every version of the artifact, would also hide earlier
versions that may be real jars. The default is strict: an unreachable
repository, a missing POM, or an unreadable jar means a JVM library and the run
goes on, because a missed record costs one ordinary run while a wrong record
hides a real library from every user.

## 2. The record is the run's outcome

On the verdict, `prepare` writes the artifact's `index.json` as the single
`not-for-native-image` entry, with a `reason` naming the evidence for a reader
who has not seen the run and, when the artifact name carries a platform suffix,
a `replacement` hint naming the JVM artifact to try. It adds no version, test,
or metadata. `validateIndexFiles` and the pre-publication gate
(§FS-local-ci-equivalent-verification.2) accept the index-only tree, the commit
is checkpointed, and `prepare` exits `22` straight to `publish`: no setup,
repair, exploration, native check, finalization, or audit runs.

`publish` pushes the index with its descriptor and no execution metrics, as it
does a skip record (§FS-unsupportable-version-diagnosis.3), and the trusted
publisher renders the not-for-native-image pull request that fixes the issue
(§AR-not-for-native-image-publication). A record the checks reject is a bad
input, so the issue reaches a maintainer instead of returning to the queue
(§FS-human-intervention-policy).
