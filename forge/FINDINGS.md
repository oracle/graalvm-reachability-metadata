# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-09-21 — org.apache.avro:avro:1.12.2 (#9363)

**Runtime-lambda re-scope left unjustified metadata**

The positive ReflectionUtil.getConstructorAsFunction scenario had been dropped after Native Image runtime lambda definition failed, but the contribution still shipped its manually added lambda registration and retained test-only ConstructedWithString metadata. That left requested metadata without the public-API test required by FS-test-contract.1.5 and FS-test-contract.2.7. The scenario itself is a valid FS-test-contract.4.3.2 re-scope: it concretely uses runtime LambdaMetafactory class definition, Native Image reports UnsupportedFeatureError, Avro catches Throwable and returns null so the refusal cannot be verified, and the remaining public-API tests still pass the repair coverage gate.
## 2026-09-22 — org.relaxng:jing:20181222 (#10162)

**Stale generated library coverage statistics**

The committed stats did not match deterministic generation from the contribution: the first Forge finalization pass changed instruction/line/method covered counts from 14944/3138/902 to 14986/3146/904. The resulting publishable-tree mutation proved the checked-in derived statistics were stale and required regeneration before approval.

## 2026-09-22 — org.apache.tomcat.embed:tomcat-embed-core:11.0.22 (#10144)

**Generated metadata captured machine-local temporary resources**

The contribution added absolute /tmp/junit... resource globs for HostConfig descriptor defaults and retained stale JAAS temporary-file globs in test-only metadata. Machine-local temporary paths must use normal file APIs rather than Native Image resource metadata under FS-test-contract.4.4.

## 2026-09-22 — org.apache.tomcat.embed:tomcat-embed-core:11.0.22 (#10144)

**Completed dynamic-access classes lacked dedicated test files**

The exhaust report marked org.apache.naming.factory.DataSourceLinkFactory$DataSourceHandler and org.apache.tomcat.util.descriptor.web.SetPublicIdRule as completed, but the original contribution had no dedicated DataSourceLinkFactoryInnerDataSourceHandlerTest or SetPublicIdRuleTest. This violated the one-file-per-dynamic-access-class requirement in §FS-test-contract.1.8.

## 2026-09-21 — org.jetbrains.kotlin:kotlin-daemon-embeddable:2.4.20 (#9926)

**Generated tests violated metadata-generation and bounded-wait contracts**

The added build.gradle generated reflection-config.json and resource-config.json under META-INF/native-image, contrary to FS-test-contract.2.7, and the loopback socket test left accept and read operations unbounded, contrary to FS-test-contract.1.6. These were contribution-local violations. Finalization passing all three native lanes after removal also proved the generated legacy configuration placeholders were unnecessary.

## 2026-09-21 — net.bytebuddy:byte-buddy-agent:1.12.4 (#9360)

**Generated test shadows an optional dependency API type**

The contribution declared tests/src/net.bytebuddy/byte-buddy-agent/1.12.4/src/test/java/com/ibm/tools/attach/VirtualMachine.java in the real com.ibm.tools.attach package to make the optional J9 attachment path available. This is a source shadow for a dependency API and violates FS-test-contract.2.3 and FS-contribution-contract.4.7.

## 2026-09-21 — com.azure:azure-data-appconfiguration:1.8.4 (#10070)

**Native Image build flags were scoped too broadly**

`build.gradle` applied four class-initialization arguments to `graalvmNative.binaries.all`, contrary to the narrowest-flag requirement in `FS-test-contract.2.7`. Removing the flags reproduced image-heap failures for `NOPLoggerFactory`, `SubstituteLoggerFactory`, `Base64Variant`, and `Base64Variant$PaddingReadBehaviour`, establishing that the transitive Azure/Jackson/SLF4J paths require them; only their binary scope needed correction.

## 2026-09-20 — com.azure:azure-identity:1.18.0 (#10071)

**Native-image initialization flags were scoped to all binaries**

The coordinate build applied its class-specific initialization flags through graalvmNative.binaries.all rather than the test binary. FS-test-contract.2.7 requires native build flags to be as narrow as possible. Removing the flags reproduced the transitive Azure Core image-heap initialization failure, confirming that the flags themselves are necessary; only their binary scope needed correction.

## 2026-09-20 — org.aspectj:aspectjweaver:1.9.21.1 (#9373)

**Test-created native flag and weakened runtime coverage**

The contribution rewrote a test class file to preview bytecode and added --enable-preview to the JVM and Native Image solely for that test, violating the native flag necessity rule in FS-test-contract.2.7 because the need originated in test code rather than AspectJ. It also removed the existing generated-concrete-aspect behavior from ClassLoaderWeavingAdaptorTest instead of preserving that meaningful behavior, violating the no-weakening repair rule in FS-test-contract.2.9.

## 2026-09-20 — org.checkerframework:checker-qual:3.46.0 (#9374)

**Generated Java test class was not public**

The added Checker_qualTest top-level class was declared package-private, violating FS-test-contract.1.2, which requires every top-level test class to be public. The violation was wholly within the target coordinate's new test source and was repairable under FS-contribution-contract.5.1.

## 2026-09-18 — org.xerial.snappy:snappy-java:1.1.0.1 (#10048)

**Native Image coverage was bypassed by test-created runtime class loading**

The original test created child-loaded copies of the library and test providers with URLClassLoader.defineClass, then wrapped the entire library exercise in NativeImageSupport.runToleratingUnsupportedFeature. Because the unsupported operation came from the test harness rather than unavoidable library behavior, Native Image could accept the class-definition failure before executing the Snappy assertions, violating the native-execution and no-runtime-class-definition rules. The accompanying --add-opens native build flag was also unnecessary for the repaired public-API path.

## 2026-09-18 — io.micronaut:micronaut-http-client-core:5.1.11 (#10079)

**Test dependencies pin the requested artifact version**

The coordinate build script hardcoded version 5.1.11 for micronaut-http-client and micronaut-inject-java instead of using the TCK-selected library version. This violated the version-agnostic test requirement in §FS-test-contract.2.5 because a copied suite would continue resolving those dependencies at 5.1.11.

## 2026-09-15 — org.springframework.integration:spring-integration-sftp:7.1.1 (#9952)

**Unbounded asynchronous reply waits in generated tests**

Two SFTP outbound-gateway assertions used QueueChannel.receive() without a timeout, so a missing reply could wait indefinitely in violation of §FS-test-contract.1.6.
## 2026-09-15 — org.apache.sshd:sshd-core:2.18.0 (#9958)

**New-library contribution has zero dynamic-access coverage**

The submitted statistics reported 0/6 covered dynamic-access calls, violating the above-20% gate for library-new-request contributions in §FS-contribution-contract.3.

## 2026-09-10 — org.apache.sshd:sshd-sftp:2.18.0 (#9959)

**Library tests rely on prohibited Native Image class-initialization flags**

The coordinate's build.gradle adds --initialize-at-build-time for Apache SSHD and SLF4J classes. This violates §FS-test-contract.2.7, which permits only --add-opens/--add-exports native configuration in a test build. I removed the flag and preserved all three scenarios while replacing the rooted server filesystem with SSHD's native filesystem, but the current-defaults lane still failed because Native Image embedded service-discovered RootedFileSystemProvider and SftpFileSystemProviderFacade instances. After reverting that unsuccessful repair, the exact finalization command passed current-defaults but failed future-defaults because sun.nio.fs.LinuxFileSystemProvider also requires prohibited build-time initialization; see forge/logs/org.apache.sshd:sshd-sftp:2.18.0/local-review-finalization/review_future-defaults_latest_GraalVM_test-2026-09-10T07-12-43-936009Z.log. No compliant contribution-local repair was established, the evidence does not prove a shared repository defect, and §FS-contribution-contract.5.4 is not established, so §FS-contribution-contract.5.5 applies.

## 2026-09-07 — org.apache.activemq:artemis-jms-client:2.36.0 (#8921)

**Cloned test projects omit required native metadata and violate test visibility and timeout rules**

The new 2.36.0 test-only reachability metadata omitted the inherited embedded-server registrations, causing nativeTest to fail while initializing ActiveMQServerLogger. In addition, ArtemisJmsClientTest in both 2.36.0 and 2.50.0 was package-private and used a 1-second JMS receive timeout, violating the public top-level test-class requirement and the 10-second minimum explicit I/O timeout.
## 2026-09-03 — io.github.microcks:microcks-testcontainers:0.5.0 (#9686)

**New-library contribution misses the dynamic-access gate and includes build logic**

Review Signal #6 was violated at `stats/io.github.microcks/microcks-testcontainers/0.5.0/stats.json`: the submitted evidence reported 0/4 dynamic-access calls (0%), but a new-library contribution with calls to cover must exceed 20%. Review Signal #1's single-library scope was also violated by the unrelated repository-wide change to `tests/tck-build-logic/src/main/java/org/graalvm/internal/tck/GrypeTask.java`; build logic is not a supporting file for this coordinate.
## 2026-09-03 — org.apache.kafka:kafka-streams:4.3.1 (#9764)

**Issue-requested reachability metadata is missing**

Issue #9764 explicitly requests reflection access to the `topologyMetadata` field on `org.apache.kafka.streams.KafkaStreams` and the `sourceTopicsForStore(String, String)` method on `org.apache.kafka.streams.processor.internals.TopologyMetadata`. Neither registration appears in `metadata/org.apache.kafka/kafka-streams/4.3.1/reachability-metadata.json`, and the new tests only use topology construction and `TopologyTestDriver`; they do not instantiate `KafkaStreams` or exercise the reporter-described path through a public library API. Rule 5 requires issue-requested metadata, appropriately conditioned when the request omitted conditions, plus a public-API test that exercises it.
## 2026-09-07 — io.opentelemetry.proto:opentelemetry-proto:1.10.0-alpha (#9910)

**Coverage update weakens an existing passing test**

In tests/src/io.opentelemetry.proto/opentelemetry-proto/1.10.0-alpha/src/test/java/io_opentelemetry_proto/opentelemetry_proto/Opentelemetry_protoTest.java, the branch removed Exemplar construction and its assertions from histogramMetricRepresentsDistributionBucketsAndExemplars. This violates the enumerated no-scope-creep/no-fix-by-weakening rule: a library-update contribution must not remove or weaken existing passing test coverage.
## 2026-09-06 — org.springframework:spring-webflux:6.0.0 (#9403)

**Explicit I/O timeout below the 10-second floor**

In tests/src/org.springframework/spring-webflux/6.0.0/src/test/java/org_springframework/spring_webflux/InvocableHandlerMethodTest.java, the reactive request wait used Duration.ofSeconds(5). This violates the enumerated 10-second minimum for explicit request/I/O timeouts in §FS-test-contract.1.7.
## 2026-09-08 — org.neo4j.bolt:neo4j-bolt-connection-netty:4.0.0 (#9390)

**Explicit test timeouts below the 10-second floor**

The new Neo4j_bolt_connection_nettyTest.java configured database transaction timeouts of 1 and 2 seconds and a server-advertised connection read timeout of 7 seconds (with a matching assertion). These concrete explicit database/read timeouts violate the 10-second minimum in §FS-test-contract.1.7.
## 2026-09-07 — io.micronaut:micronaut-buffer-netty:5.1.13 (#9828)

**Application context closes while eager bean processing is still running**

In Micronaut_buffer_nettyTest.java, both ApplicationContext.run(...) calls enable asynchronous eager bean processing. The native test lane completed its assertions but then emitted an uncaught NullPointerException from DefaultBeanContext.processParallelBeans after the contexts had closed. This violates the test contract requirement that background resources be cleanly bounded and closed.

## 2026-09-02 — org.springframework:spring-web:5.3.32 (#9402)

**Runtime repair deletes existing test coverage**

Rule 4.8 (fix by weakening) was violated in `tests/src/org.springframework/spring-web/5.3.32`: the copied 5.3.18 suite deleted `JettyClientHttpResponseTest.java` and removed its Jetty dependency instead of adapting the failing runtime path while preserving the test's status, headers, cookies, and body assertions.
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
## 2026-09-02 — org.xerial.snappy:snappy-java:1.0.5.3 (#9404)

**Native-only SnappyError is swallowed without UnsupportedFeatureError verification**

In tests/src/org.xerial.snappy/snappy-java/1.0.5.3/src/test/java/org_xerial_snappy/snappy_java/SnappyLoaderTest.java, hasUnsupportedSnappyNativeLoaderFailure accepted any native-runtime org.xerial.snappy.SnappyError whose stack mentioned SnappyLoader.injectSnappyNativeLoader. This tolerates a recognized Native Image failure without verifying it through NativeImageSupport.isUnsupportedFeatureError, violating the enumerated Native Image dodging rule. The smallest compliant correction exposes that nativeTest is not green because snappy-java discards the underlying UnsupportedFeatureError cause.

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
