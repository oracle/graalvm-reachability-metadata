# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-08-28 — org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3 (#7020)

**New-library change included unrelated library metadata**

Review Signal #1 requires a new-library change to contain only one target library and its supporting test files, but metadata/org.hibernate.validator/hibernate-validator/7.0.4.Final/reachability-metadata.json was also modified alongside the org.springdoc addition.
