# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-09-02 — org.apache.activemq:artemis-jms-client:2.56.0 (#9574)

**Generated repair changes a baseline suite and violates mandatory test bounds**

The eventual diff modified the existing 2.28.0 test and test-only metadata even though the repair has a dedicated 2.56.0 project, violating the target-source and no-scope-creep rules. In tests/src/org.apache.activemq/artemis-jms-client/2.56.0/src/test/java/org_apache_activemq/artemis_jms_client/ArtemisJmsClientTest.java, the top-level test class was package-private, consumer.receive(1000) used a 1-second messaging timeout below the mandatory 10-second floor, and waitForActivation(1, TimeUnit.MINUTES) did not keep the individual test below 60 seconds.

## 2026-09-02 — org.eclipse.jetty:jetty-util:12.0.9 (#8928)

**Test-only resource bundle shipped as library metadata**

metadata/org.eclipse.jetty/jetty-util/12.0.9/reachability-metadata.json registered the bundle org_eclipse_jetty.jetty_util.loader even though that bundle is supplied only by tests/src/org.eclipse.jetty/jetty-util/12.0.9/src/test/resources/org_eclipse_jetty/jetty_util/loader.properties. Shipping test-owned metadata violates the required library/test metadata split.

## 2026-09-02 — org.springframework.amqp:spring-rabbitmq-client:4.2.0-M1 (#9467)

**Test messaging timeouts violate the 10-second floor**

In tests/src/org.springframework.amqp/spring-rabbitmq-client/4.2.0-M1/src/test/java/org_springframework_amqp/spring_rabbitmq_client/Spring_rabbitmq_clientTest.java, the newly added test project configured 250 ms publish, completion, request, and graceful-shutdown timeouts and used 1-second bounded waits. These explicit messaging/client timeouts are below the mandatory 10-second minimum in §FS-test-contract.1.7.
## 2026-09-02 — org.springframework:spring-websocket:6.2.10 (#8969)

**Version-pinned supporting dependency in reusable test project**

Review Signal #4 is violated in tests/src/org.springframework/spring-websocket/6.2.10/build.gradle: spring-messaging is pinned to 6.2.10 even though this test project must remain reusable across supported Spring Framework versions.

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
