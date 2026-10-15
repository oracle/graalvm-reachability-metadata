# GOAL-consumer-evidence: Supported libraries shown working in real applications

For every library it covers, the project should hold scenarios that use the
library as an application does, each passing on the JVM and on Native Image
with the shipped metadata resolved the way a consumer resolves it. A scenario
that cannot pass natively is dropped and reported rather than kept behind a
guard, so every scenario the project holds is evidence that real use works.
§GRUND-real-use-motivation

# GOAL-reviewable-metadata-gaps: Metadata gaps surface as reviewable candidates

Whatever a realistic scenario needs beyond the shipped metadata should surface
as concrete entries a maintainer can review — kept test-local, already in
shipped form, and listed where the maintainer reads them — never as a silent
change to `metadata/` or to the `tests/` suite. Promoting an entry into shipped
metadata stays a separate change held to the repository's own rules
(§root/GOAL-tested-metadata).
§GRUND-real-use-motivation
