# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-08-31 — org.apache.tomcat.embed:tomcat-embed-core:11.0.18 (#8328)

**Reporter-requested metadata shipped only as test metadata**

The issue-required org.apache.coyote.AbstractProtocol.setProperty(String, String) registration was present only in tests/src/org.apache.tomcat.embed/tomcat-embed-core/11.0.18/src/test/resources/META-INF/native-image/reachability-metadata.json, while metadata/org.apache.tomcat.embed/tomcat-embed-core/11.0.18/reachability-metadata.json shipped getProperty but not setProperty. Test-only metadata masked the missing consumer registration, violating the rule that issue-requested metadata must be present in shipped metadata and exercised through the library's public API.

## 2026-08-29 — org.junit.platform:junit-platform-commons:1.12.0 (#9603)

**Javac repair adds unrelated coverage behavior**

The added discoversLicenseNoticeResources test in tests/src/org.junit.platform/junit-platform-commons/1.12.0/src/test/java/org_junit_platform/junit_platform_commons/ReflectionUtilsTest.java was unrelated to the ClassFilter compilation failure and broadened the repair with a new resource-coverage path, violating the enumerated no-scope-creep rule in the test contract.
## 2026-08-29 — org.hibernate:hibernate-core:6.1.0.Final (#9324)

**Serialization metadata omits Object constructor registration**

`metadata/org.hibernate/hibernate-core/6.1.0.Final/reachability-metadata.json` registered `java.lang.Object` when `SerializationHelper` is reached but omitted its no-argument constructor. The local intervention record documented resulting `MissingReflectionRegistrationError` failures in three normal Hibernate serialization tests, violating the required native-execution gate.
## 2026-08-29 — org.junit.jupiter:junit-jupiter-params:6.1.0 (#9208)

**Top-level test class is not public**

tests/src/org.junit.jupiter/junit-jupiter-params/6.1.0/src/test/java/org_junit_jupiter/junit_jupiter_params/JunitJupiterParamsTest.java declared the top-level JunitJupiterParamsTest with package-private visibility. This concretely violates the public top-level test class requirement in §FS-test-contract.1.2.

## 2026-08-29 — org.apache.calcite:calcite-core:1.35.0 (#446)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.

## 2026-08-28 — org.junit.platform:junit-platform-commons:1.11.0 (#9209)

**Resource dynamic-access coverage regresses beyond the fixes-javac limit**

In `stats/org.junit.platform/junit-platform-commons/1.11.0/stats.json`, resource dynamic-access coverage is 66.67% (2/3), down from 100% (2/2) for 1.8.2: a 33.33 percentage-point drop. The `fixes-javac-fail` rule applies the 20-point limit to present breakdown entries as well as the overall report. Restore coverage for the uncovered `ModuleUtils$ModuleReferenceResourceScanner.loadResourceUnchecked` call site or provide a concrete, credible explanation of why the changed upstream API surface makes that call site unsuitable for coverage.
## 2026-08-27 — org.junit.jupiter:junit-jupiter-api:5.11.4 (#9206)

**Repair drops below the metadata-entry guardrail**

The resolved evidence reports 4 library metadata entries plus 13 test-only entries for 5.11.4 (17 total), versus 70 for 5.8.2. Since 17 is below 25% of 70 (17.5), this violates the fixes-java-run-fail metadata-entry guardrail. Regenerate enough justified metadata to clear the threshold or provide a concrete, credible explanation of the changed API/runtime surface.
## 2026-08-28 — org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3 (#7020)

**New-library change included unrelated library metadata**

Review Signal #1 requires a new-library change to contain only one target library and its supporting test files, but metadata/org.hibernate.validator/hibernate-validator/7.0.4.Final/reachability-metadata.json was also modified alongside the org.springdoc addition.
## 2026-08-27 — io.netty:netty-common:5.0.0.Alpha2 (#9307)

**Dynamic-access resource coverage regresses by 25 percentage points**

In stats/io.netty/netty-common/5.0.0.Alpha2/stats.json, resources coverage is 75% (3/4), versus 100% (1/1) for 5.0.0.Alpha1. That 25-point drop exceeds the fixes-javac-fail limit, and the supplied evidence gives no concrete explanation or replacement coverage. Restore resource coverage or provide a concrete, credible explanation for the regression.
