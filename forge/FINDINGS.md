# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-09-07 — io.opentelemetry.proto:opentelemetry-proto:1.10.0-alpha (#9910)

**Coverage update weakens an existing passing test**

In tests/src/io.opentelemetry.proto/opentelemetry-proto/1.10.0-alpha/src/test/java/io_opentelemetry_proto/opentelemetry_proto/Opentelemetry_protoTest.java, the branch removed Exemplar construction and its assertions from histogramMetricRepresentsDistributionBucketsAndExemplars. This violates the enumerated no-scope-creep/no-fix-by-weakening rule: a library-update contribution must not remove or weaken existing passing test coverage.

## 2026-09-07 — io.micronaut:micronaut-http-server:5.1.13 (#9832)

**Companion dependencies pin the tested library version**

Review Signal #4 is violated in tests/src/io.micronaut/micronaut-http-server/5.1.13/build.gradle: the Netty server, HTTP client, and Jackson databind companion dependencies hardcode 5.1.13. This prevents the suite from resolving those matching Micronaut modules at the TCK-selected tested version when the same test is reused for later supported versions.

## 2026-09-07 — io.micronaut:micronaut-core:5.0.0 (#9799)

**Missing minimum dynamic-access coverage evidence**

Review Signal #6 was violated in stats/io.micronaut/micronaut-core/5.0.0/stats.json: dynamicAccess was N/A, while resolved evidence described dynamic-access metadata and supplied no covered call sites, so the branch had no credible evidence that non-zero dynamic access exceeded 20%. Regenerating the report exposed 83 calls and only 10 covered (12.05%) before test expansion.

## 2026-09-07 — org.flywaydb:flyway-core:13.5.0 (#9842)

**Java-run repair adds unrelated internal-API coverage**

`tests/src/org.flywaydb/flyway-core/13.5.0/src/test/java/flyway/MergeUtilsTest.java` directly exercised the internal `org.flywaydb.core.internal.util.MergeUtils` API solely to increase dynamic-access coverage. This was unrelated to the Jackson runtime dependency repair and violated the enumerated meaningful-public-API and no-scope-creep test rules.

## 2026-09-07 — org.junit.platform:junit-platform-reporting:6.1.0 (#9212)

**Repair contribution changes unrelated coordinates**

The proposed diff added metadata, index changes, and stats for org.junit.platform:junit-platform-commons:6.1.0 and org.junit.platform:junit-platform-engine:6.1.0. The closed-file-set and one-library/one-version rules require this fixes-native-image-run-fail contribution to remain inside the target org.junit.platform:junit-platform-reporting:6.1.0 coordinate and its supporting files.
## 2026-09-07 — org.bouncycastle:bcpkix-jdk18on:1.77 (#8922)

**Test-created serialization metadata shipped as library metadata**

The custom ObjectInputStream/ObjectOutputStream subclasses in X509CRLHolderTest.java and X509CertificateHolderTest.java replaced the library's normal byte[] serialization payload with test-selected short[]/int[] values solely to create distinct descriptor paths. This was a direct serialization shortcut, and it caused the test-created short[] registration plus stale org_bouncycastle test-resource entries to ship in metadata/org.bouncycastle/bcpkix-jdk18on/1.77/reachability-metadata.json, violating the enumerated no-shortcuts and no-test-only-shipped-metadata rules.

## 2026-09-06 — org.apache.activemq:activemq-broker:6.0.0 (#8927)

**Split test project retained an invalid baseline test contract**

In `tests/src/org.apache.activemq/activemq-broker/6.3.0/src/test/java/org_apache_activemq/activemq_broker/ActivemqBrokerTest.java`, the newly added top-level test class was package-private, violating test-contract rule 1.2. That test and the modified 6.0.0 counterpart also used unbounded broker lifecycle waits and performed broker cleanup only after the connection path succeeded, violating rule 1.6. The new 6.3.0 project's `gradle.properties` additionally still identified the 6.0.0 library and metadata directories instead of its own coordinate.
## 2026-09-06 — io.micronaut:micronaut-management:5.1.13 (#9825)

**Companion Micronaut dependencies pin the tested library version**

Review Signal #4 / FS-test-contract.2.5 is violated in tests/src/io.micronaut/micronaut-management/5.1.13/build.gradle: the HTTP server, HTTP client, and Jackson companion modules hardcode 5.1.13, so the test project does not remain version-agnostic when the tested Micronaut version changes.
## 2026-09-06 — io.micronaut.serde:micronaut-serde-support:3.1.1 (#9837)

**Version-pinned companion serialization dependency**

Review Signal #4 was violated in tests/src/io.micronaut.serde/micronaut-serde-support/3.1.1/build.gradle: micronaut-serde-jackson was pinned to 3.1.1, so the suite would not track the tested Micronaut Serde version when reused for another supported version.

## 2026-09-06 — io.netty:netty-codec:5.0.0.Alpha1 (#9762)

**Unsupported-feature catch masks statically available class resolution**

In tests/src/io.netty/netty-codec/5.0.0.Alpha1/src/test/java/io_netty/netty_codec/ClassLoaderClassResolverTest.java, both tests caught Error and accepted NativeImageSupport.isUnsupportedFeatureError even though the target class name is fixed, the class is included in the image, and the behavior does not require a class discovered after image build. This applies the sole open-ended dynamic-class-loading exception outside its permitted scope and could make the native tests pass without executing their assertions.
## 2026-09-06 — io.netty:netty-codec:4.2.2.Final (#9761)

**Native-image repair deletes the failing checksum test**

The branch deletes tests/src/io.netty/netty-codec/4.1.42.Final/src/test/java/io_netty/netty_codec/ByteBufChecksumInnerReflectiveByteBufChecksumTest.java after that test exposed the CleanerJava24/SharedArena native failure. Deleting the failing test to make the native lane green is the enumerated fix-by-weakening violation in FS-contribution-contract.4.8 / FS-test-contract.2.9 and does not demonstrate that the native-image runtime path was repaired.

## 2026-09-05 — com.github.seregamorph:spring-test-smart-context:1.0 (#9677)

**Dynamic-access coverage does not meet the new-library minimum**

Review Signal #6 was violated at stats/com.github.seregamorph/spring-test-smart-context/1.0/stats.json: the submitted evidence reported 0 of 1 dynamic-access calls covered (0%), while new-library requests with non-zero calls require coverage above 20%.
## 2026-09-05 — org.springframework.boot:spring-boot-testcontainers:4.1.1 (#9682)

**Version-pinned companion Spring Boot test dependency**

Review Signal #4 was violated in tests/src/org.springframework.boot/spring-boot-testcontainers/4.1.1/build.gradle: spring-boot-test was pinned to 4.1.1, so this test suite would keep using that companion module when exercised against another supported spring-boot-testcontainers version.

## 2026-09-05 — org.springframework.cloud:spring-cloud-vault-config:5.0.2 (#9705)

**New-library request included an unrelated shared GrypeTask behavior change**

Review Signal #1 requires a new-library request to stay scoped to one target library and its supporting test files. tests/tck-build-logic/src/main/java/org/graalvm/internal/tck/GrypeTask.java changed shared Docker scanning behavior to silently accept an empty image diff, which is unrelated to adding org.springframework.cloud:spring-cloud-vault-config:5.0.2.

## 2026-09-02 — org.springframework.boot:spring-boot-amqp:4.2.0-M1 (#9468)

**Explicit messaging timeout is below the 10-second minimum**

FS-test-contract.1.7 requires every explicit messaging timeout to be at least 10 seconds. tests/src/org.springframework.boot/spring-boot-amqp/4.2.0-M1/src/test/java/org_springframework_boot/spring_boot_amqp/Spring_boot_amqpTest.java configured the AMQP client completion timeout as 750ms in both direct property setup and the auto-configuration property test.
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
