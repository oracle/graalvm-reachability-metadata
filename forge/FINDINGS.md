# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-08-29 — org.hibernate:hibernate-core:6.1.0.Final (#9324)

**Serialization metadata omits Object constructor registration**

`metadata/org.hibernate/hibernate-core/6.1.0.Final/reachability-metadata.json` registered `java.lang.Object` when `SerializationHelper` is reached but omitted its no-argument constructor. The local intervention record documented resulting `MissingReflectionRegistrationError` failures in three normal Hibernate serialization tests, violating the required native-execution gate.
## 2026-08-29 — org.apache.calcite:calcite-core:1.35.0 (#446)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.

## 2026-08-28 — org.junit.platform:junit-platform-commons:1.11.0 (#9209)

**Resource dynamic-access coverage regresses beyond the fixes-javac limit**

In `stats/org.junit.platform/junit-platform-commons/1.11.0/stats.json`, resource dynamic-access coverage is 66.67% (2/3), down from 100% (2/2) for 1.8.2: a 33.33 percentage-point drop. The `fixes-javac-fail` rule applies the 20-point limit to present breakdown entries as well as the overall report. Restore coverage for the uncovered `ModuleUtils$ModuleReferenceResourceScanner.loadResourceUnchecked` call site or provide a concrete, credible explanation of why the changed upstream API surface makes that call site unsuitable for coverage.
## 2026-08-28 — org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3 (#7020)

**New-library change included unrelated library metadata**

Review Signal #1 requires a new-library change to contain only one target library and its supporting test files, but metadata/org.hibernate.validator/hibernate-validator/7.0.4.Final/reachability-metadata.json was also modified alongside the org.springdoc addition.
## 2026-08-27 — io.netty:netty-common:5.0.0.Alpha2 (#9307)

**Dynamic-access resource coverage regresses by 25 percentage points**

In stats/io.netty/netty-common/5.0.0.Alpha2/stats.json, resources coverage is 75% (3/4), versus 100% (1/1) for 5.0.0.Alpha1. That 25-point drop exceeds the fixes-javac-fail limit, and the supplied evidence gives no concrete explanation or replacement coverage. Restore resource coverage or provide a concrete, credible explanation for the regression.
