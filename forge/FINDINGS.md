# Forge pre-push review findings

Rendered by Forge from the pre-push branch review of §FS-local-branch-review.
Newest entry first; every non-approval is recorded, including one a repair later cleared.

## 2026-10-08 — org.springframework.batch:spring-batch-core:6.0.4 (#9286)

**New-library contribution modifies another coordinate**

§FS-contribution-contract.2 limits this contribution to org.springframework.batch:spring-batch-core:6.0.4 and its supporting files, but the diff modifies metadata/org.hsqldb/hsqldb/2.7.3/reachability-metadata.json. The local review evidence independently flags that same path in repo_fix_paths and requires human intervention. I checked whether this could be repaired within the Spring Batch file set, but the change belongs to the HSQLDB dependency coordinate; removing, relocating, or landing it requires an edit or separate contribution for another coordinate. Under §FS-contribution-contract.5.2 and §FS-contribution-contract.5.5, a maintainer must split or land the HSQLDB metadata update separately, then rebase or regenerate this contribution.
## 2026-10-08 — org.springframework.boot:spring-boot-resttestclient:4.1.1 (#9713)

**Supporting dependency pins the target library version**

The test project's `build.gradle` pinned `org.springframework.boot:spring-boot-restclient` to `4.1.1`, so the suite would not follow the TCK-selected Spring Boot version, violating the version-agnostic test requirement.

## 2026-10-06 — org.springframework.boot:spring-boot-micrometer-tracing:4.1.1 (#9732)
## 2026-10-07 — io.micrometer:micrometer-observation-test:1.17.1 (#9727)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.

## 2026-10-06 — org.apache.activemq:activemq-client:6.3.0 (#10600)

**Generated statistics and test-only metadata were not fully finalized**

The first required Forge finalization pass changed the publishable tree and exited nonzero: it regenerated stale library instruction/line coverage values in `stats/org.apache.activemq/activemq-client/6.3.0/stats.json` and normalized section ordering in the test-only `reachability-metadata.json`. A publishable contribution must contain the deterministic finalized outputs, so this was repaired within the allowed coordinate files under FS-contribution-contract.5.1.

## 2026-10-06 — org.apache.activemq:activemq-client:6.2.7 (#10593)

**ActiveMQ test teardown waits indefinitely and suppresses shutdown failures**

The modified ActiveMQClientTest teardown called unbounded BrokerService.waitUntilStopped() and caught and logged stop failures, violating the bounded-test requirement in FS-test-contract.1.6 and allowing cleanup failure to be hidden. This was contribution-local and repairable under FS-contribution-contract.5.1.

## 2026-10-06 — org.apache.activemq:activemq-client:6.0.0 (#10536)

**Generated broker tests exceed the per-test timeout bound**

The newly added ActiveMQSessionTest and ProducerThreadTest called BrokerService.waitUntilStarted(), whose ActiveMQ 6.0.0 default timeout is 600000 ms, and waitUntilStopped(), which waits on the stopped latch without a timeout. This violated the 60-second per-test and bounded-wait requirements in FS-test-contract.1.6.

## 2026-10-06 — org.springframework:spring-test:7.0.0 (#9401)

**Native-only AOT failure is accepted as test success**

MergedContextConfigurationRuntimeHintsTest caught the library-specific IllegalStateException stating that AOT processing cannot run during AOT runtime and treated that exception as success. This made Native Image pass with behavior different from the JVM and violated FS-test-contract.4.2; it was not the sanctioned UnsupportedFeatureError proof for open-ended dynamic class loading.
## 2026-10-06 — org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5 (#10516)

**Generated test configuration exceeded timeout and module-opening limits**

Mqttv5ClientTest allowed broker startup to wait 120 seconds, violating the 60-second per-test bound in FS-test-contract.1.6. The build also opened sun.nio.ch to JVM and Native Image without satisfying the uniform-necessity rule in FS-test-contract.2.7; Java testing and all three finalization lanes passed after that opening was removed. The narrower java.net opening remains justified: removing it reproducibly failed NetworkModuleServiceTest with InaccessibleObjectException in the library's NetworkModuleService.setURIField path.

## 2026-10-05 — org.flywaydb:flyway-core:10.15.0 (#10518)

**Dynamic-access classes lacked dedicated test files**

The resolved dynamic-access report listed covered call sites in org.flywaydb.core.internal.license.VersionPrinter and org.flywaydb.core.internal.resource.classpath.ClassPathResource, but the suite had no dedicated VersionPrinterTest.java or ClassPathResourceTest.java. This violated the one-test-file-per-dynamic-access-class requirement in §FS-test-contract.1.8.

## 2026-10-05 — org.flywaydb:flyway-database-postgresql:10.10.0 (#10555)

**Test-only reachability metadata keys were not deterministically ordered**

The initial test-only reachability-metadata.json placed the top-level resources key before reflection instead of using the repository's normalized JSON key ordering. Forge finalization detected this as a publishable-tree change and reordered the keys without changing their entries or semantics.
## 2026-10-05 — dev.langchain4j:langchain4j-jina:1.21.0-beta31 (#10469)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.

## 2026-10-04 — io.lettuce:lettuce-core:7.0.0.RELEASE (#10430)

**Compile repair removed meaningful runtime coverage**

The post-generation intervention violated the fix-by-weakening rule in FS-test-contract.2.9 and failed the large-report repair coverage gate: it removed 23 inherited connection and command tests rather than preserving their behavior, reducing both overall and reflection dynamic-access coverage from 52/55 (94.5455%) to 16/55 (29.0909%). The recorded failures were caused by transitive Netty 4.2.4 reaching Arena.ofShared while Native Image shared-arena support was disabled, not by the test behavior or missing reachability metadata.

## 2026-10-04 — ch.qos.logback:logback-classic:1.6.2 (#10425)

**Runtime repair weakened an unrelated passing test**

The generated branch rewrote PackagingDataCalculatorTest by removing its platform-context-class-loader scenario and replacing the real throwable stack with a synthetic frame, even though the recorded native run showed that original test passing. That was a fix-by-weakening violation of the test scope contract, unrelated to the reported RemoteAppenderStreamClient broken-pipe failure.

## 2026-10-04 — org.apache.tomcat.embed:tomcat-embed-el:10.1.0 (#10432)

**Java runtime repair broadened into unrelated test coverage**

The generated contribution violated the no-scope-creep and fix-by-weakening boundary in §FS-test-contract.2.9 and §FS-contribution-contract.4.8: beyond adapting the failing StaticFieldELResolver type assertion, it added an unrelated EL lambda scenario, added a malformed-expression MessageFactory scenario, and rewrote the already-passing FunctionMapper scenario solely to increase dynamic-access coverage. The observed JVM failure concerned only StaticFieldELResolver.getType returning null in Tomcat 10.1.

## 2026-10-03 — gg.jte:jte-runtime:3.2.4 (#9786)

**Dynamic-access classes shared a generic test file**

The original Jte_runtimeTest.java covered dynamic access in both gg.jte.runtime.RuntimeTemplateLoader and gg.jte.runtime.Template. The test contract requires each dynamic-access class to have its own dedicated test file.
## 2026-10-03 — org.jetbrains.kotlin:kotlin-reflect:2.5.0-Beta1 (#10368)

**Coverage tests bypass the library public API and force an alternate implementation mode**

The contribution directly imported and exercised kotlin.reflect.jvm.internal DescriptorKindFilter, GeneratedMessageLite, and SmartList classes instead of reaching them through the public kotlin.reflect API. It also forced kotlin.reflect.jvm.loadMetadataDirectly=true for JVM and native tests even though the issue and preparation evidence required no system property and did not establish the test-contract uniform-necessity conditions for a runtime flag. These violated the meaningful public-API and native flag requirements.

## 2026-10-03 — org.eclipse.jetty:jetty-io:10.0.0 (#10386)

**Jetty I/O tests used explicit timeouts below the required floor**

The new Jetty 10 test project used 2-second waits for asynchronous I/O callbacks and a 100-millisecond IdleTimeout. These explicit I/O timeouts violated FS-test-contract.1.7, which requires at least 10 seconds to avoid agent and Native Image startup flakiness.
## 2026-10-03 — org.locationtech.jts:jts-core:1.20.0 (#9789)

**Bundled support JAR shadows the target library**

The test project added JTSTestBuilder-support.bin as a test dependency. Its embedded Maven metadata identified a jts-app 1.20.0-SNAPSHOT assembly, and the archive contained 751 org/locationtech/jts class files, including classes supplied by the target jts-core artifact. The tests could therefore execute shadow copies instead of org.locationtech.jts:jts-core:1.20.0, violating §FS-test-contract.2.3's prohibition on shadow classes for library types.

## 2026-10-03 — org.jetbrains.kotlin:kotlin-reflect:2.5.0-Beta1 (#10368)

**Generated library statistics were stale**

The first required finalization run regenerated stats.json and changed instruction coverage from 112261 to 112266 covered instructions and line coverage from 16461 to 16462 covered lines. Because the required statistics generation did not reproduce the committed file, the generated statistics were stale and needed refresh before approval.
## 2026-10-02 — org.apache.tomcat.embed:tomcat-embed-el:10.0.17 (#10319)

**Runtime repair masked a library serialization regression and required a test-only native build flag**

The contribution used CompatibleObjectOutputStream to replace Class<?>[] with String[] while serializing FunctionMapperImpl, hiding Tomcat 10.0.17's incompatible normal round trip instead of testing supported behavior; the 10.0.17 source writes Class[] while readExternal reads String[]. It also installed a test-only ExpressionFactory provider and added -H:ServiceLoaderFeatureExcludeServiceProviders solely to make that provider win in Native Image, contrary to the uniform-necessity rule for native flags. The custom provider and MessageFactory additions were unrelated expansion in a JVM runtime repair. These violated FS-test-contract.2.6, FS-test-contract.2.7, and FS-test-contract.2.9.
## 2026-10-03 — org.apache.curator:curator-client:5.8.0 (#10317)

**Test forces an unrelated transitive dependency version**

The 5.8.0 test project forced ZooKeeper from Curator 5.8.0's resolved 3.9.2 dependency to 3.5.10 solely to exercise legacy Compatibility branches. This changed the tested runtime graph and added unrelated build setup rather than adapting the test to the target version, violating the no-scope-creep requirement in FS-test-contract.2.9.
## 2026-10-03 — com.google.guava:guava:33.7.0-jre (#10313)

**Unverifiable native class-loading tolerance and missing Finalizer metadata**

ClassPathInnerClassInfoTest accepted ClassInfo.load() failures by checking the native-image runtime property and a library-wrapped ClassNotFoundException instead of positively proving GraalVM's UnsupportedFeatureError. The attempted operation loads generated bytecode from a runtime-created temporary class path after the native executable is built, so it is unsupported open-ended dynamic class loading; the library-level ClassNotFoundException leaves the refusal unverifiable. Re-scoping the test to ClassPath's public discovery API preserves meaningful native-compatible assertions and keeps the remaining shipped metadata justified. Finalization also reproduced a MissingReflectionRegistrationError for Finalizer.startFinalizer; the prior tested version contained the two FinalizableReference/Finalizer registrations required by this unchanged library behavior.

## 2026-10-02 — org.jetbrains.kotlin:kotlin-reflect:2.5.0-Beta1 (#10325)

**Latest Kotlin reflection metadata dropped established native support**

The new latest metadata bucket retained only kotlin/kotlin.kotlin_builtins and omitted conditional registrations already required by Kotlin reflection. Since Spring's unlisted kotlin-reflect 2.3.21 dependency selects the latest bucket, native runs failed first with an unresolved java.util.Set and then with missing reflection access to Executable.getParameters() and Parameter.getName(). This is a contribution-local required-CI regression repairable under contribution-contract disposition 5.1.
## 2026-10-02 — ch.qos.logback:logback-classic:1.6.0 (#10311)

**Explicit I/O timeouts below the required 10-second floor**

FS-test-contract.1.7 requires every explicit socket, connection, and bounded I/O wait to be at least 10 seconds. RemoteAppenderStreamClientTest, SocketNodeTest, and SocketReceiverTest each set SOCKET_TIMEOUT_MILLIS to 5,000 and used it for socket operations and related bounded waits.
## 2026-10-03 — com.nimbusds:nimbus-jose-jwt:4.0 (#9334)

**Unjustified Native Image URL protocol flag**

The new 4.0 test project copied `--enable-url-protocols=https` into `build.gradle` without satisfying the native-flag necessity rule. The adapted 4.0 tests use `URI` values and perform no HTTPS URL access, so the flag was not required by the library or a transitive dependency. All three Native Image lanes passed after its removal.

## 2026-10-02 — org.springframework:spring-aop:6.1.7 (#10339)

**Over-broad Spring AOP serialization condition breaks affected native consumers**

The 6.1.7 metadata made the serializable AspectJMethodBeforeAdvice entry conditional on InstantiationModelAwarePointcutAdvisorImpl. On the CI GraalVM 25.0.4+7.1, that condition activates in Spring AOT applications where the advice type is not otherwise reachable and fails during image layout with `Type not found during analysis: ... AspectJMethodBeforeAdvice`. The exact defect previously caused PR #10119's Spring AOT matrix to fail and be reverted. Because the violation is in this contribution's own metadata, it is repaired under §FS-contribution-contract.5.1.
## 2026-10-02 — org.aspectj:aspectjweaver:1.9.21.2 (#10321)

**Test-only preview configuration broadened a javac repair**

The contribution added --enable-preview to Java compilation, JVM execution, and Native Image build configuration solely to support a rewritten experimental-ASM test path. That necessity originated in test code, not AspectJ or a transitive dependency, violating the native flag rule in FS-test-contract.2.7 and broadening the compile repair beyond the changed Optional-return API contrary to FS-test-contract.2.9.
## 2026-10-02 — com.sun.xml.ws:httpspi-servlet:4.0.5 (#9946)

**Shipped metadata was not exercised by the generated tests**

Resolved review evidence reported metadata for reflective WstxInputFactory construction when DeploymentDescriptorParser is reached, but the original tests never invoked DeploymentDescriptorParser. The suite could therefore stay green without exercising the public library path that requires the shipped metadata, violating the meaningful public-API and metadata-justification requirements.

## 2026-10-02 — org.apache.sshd:sshd-common:2.18.0 (#9957)

**Generated stats omit observed dynamic-access coverage**

The committed stats reported dynamicAccess as N/A even though the local evidence identified 27 non-zero dynamic-access call sites, so the new-library coverage gate lacked credible percentage evidence. Forge finalization regenerated the report as 27/27 covered (100%; reflection 25/25 and resources 2/2).

## 2026-10-02 — org.jetbrains.kotlin:kotlin-stdlib:2.5.0-Beta1 (#10330)

**Repair coverage bypasses library visibility and carries an unnecessary Native Image flag**

The newly added KotlinGenericDeclarationKtTest suppressed INVISIBLE_MEMBER and INVISIBLE_REFERENCE to cast to kotlin.jvm.internal.KotlinGenericDeclaration and invoke its internal API, so its coverage did not satisfy the public-API must in FS-test-contract.1.3. The copied build also passed --initialize-at-build-time=kotlin.Metadata without evidence for the uniform-necessity rule in FS-test-contract.2.7; all three native lanes pass without it.
## 2026-10-02 — org.apache.curator:curator-client:5.0.0 (#10288)

**Generated coverage tests bypass supported consumer behavior**

CompactHashMapTest and CompactHashSetTest manufactured Java serialization streams naming inaccessible shaded implementation classes instead of reaching behavior through a public library API, violating §FS-test-contract.1.3 and §FS-test-contract.2.1. ClassPathInnerClassInfoTest and ClassPathInnerResourceInfoTest exercised temporary URLClassLoader and machine-local classpath discovery prohibited by §FS-test-contract.4.4 and §FS-test-contract.4.5. Re-scoping those ClassPath tests to the ordinary application class loader failed natively with an unverifiable NoSuchElementException, so the unsupported open-ended classpath-discovery scenarios were dropped under §FS-test-contract.4.3.2; the mechanism, failed re-scope, and surviving 109/123 covered calls satisfy the three repair bounds in §FS-contribution-contract.5.1. The contribution also overrode Curator's resolved ZooKeeper 3.6.0 dependency with 3.5.7 solely to assert the older Compatibility branch, rather than testing the target's normal dependency surface.

## 2026-09-21 — org.liquibase:liquibase-core:4.17.0 (#3996)

**Forge missed downstream test-version aliases before publication**

FS-library-update-tested-version-split requires Forge to catch regenerated-test incompatibility before a library-update branch becomes PR-eligible. The 4.20.0 and 4.23.0 index entries both declare test-version 4.17.0, so changes under tests/src/org.liquibase/liquibase-core/4.17.0 affect both entries in CI. Forge's alias splitter inspected only the target 4.17.0 entry, whose tested-versions list contains only 4.17.0; the publication descriptor consequently records render.alias_split as null and local verification as successful. After the scoped YAML API adaptation, ./gradlew test for 4.17.0 passes, while ./gradlew test for 4.20.0 builds successfully through the JVM lane but reports 11 native-test failures, and ./gradlew checkstyle compileTestJava for 4.23.0 fails because liquibase.hub.core and liquibase.hub.model were removed. Keeping the regenerated 4.17.0 coverage while retaining the baseline suite for these downstream aliases requires changing the artifact index and creating or selecting another version's test directory. Those are outside the permitted 4.17.0 repair file set, so this is disposition 5.3 followed by 5.5 rather than a contribution-local repair.

Infrastructure issue: https://github.com/oracle/graalvm-reachability-metadata/issues/10138 (#10138)

## 2026-09-21 — org.liquibase:liquibase-core:4.17.0 (#3996)

**Generated tests exercise prohibited OSGi class-loader behavior**

CustomChangeWrapperTest added an OSGi Bundle/Activator scenario and org.osgi dependency specifically to exercise Liquibase's OSGi class-loader branch. FS-test-contract.4.5 forbids tests targeting OSGi class-loader paths because they depend on runtime class-loading behavior Native Image cannot support.

## 2026-10-02 — io.micronaut:micronaut-context:5.1.3 (#9803)

**Unnecessary compiler dependency on the runtime test classpath**

The coordinate build declared io.micronaut:micronaut-inject-java:5.1.3 as testImplementation even though the compiler was already present in the required, version-aligned testAnnotationProcessor configuration. This unnecessarily broadened the test classpath and hardcoded a support-module version, contrary to the minimal-scope requirement in §root/FS-test-contract.2.9. The complete finalization pass succeeded after removal, confirming that the extra testImplementation dependency was not needed.

## 2026-10-02 — com.squareup.okhttp3:okhttp:3.9.0 (#9336)

**Generated repair uses a shadow Android API to manufacture coverage**

The added tests/src/com.squareup.okhttp3/okhttp/3.9.0/src/test/java/android/security/NetworkSecurityPolicy.java shadowed a real Android framework type, and AndroidPlatformTest depended on that fake type to activate an Android-only library path on the desktop test runtime. The contribution also changed the inherited X509TrustManagerExtensions stub to force the desired fallback. This violates FS-test-contract.2.3 and adds unrelated synthetic coverage to a javac repair rather than narrowly adapting the failing TrustRootIndex call.

## 2026-10-02 — org.hibernate:hibernate-core:6.1.0.Final (#9119)

**Modified top-level test class is not public**

FS-test-contract.1.2 requires every top-level test class to be public. The contribution modified AbstractHibernateTest.java but left its top-level abstract test class package-private, while newly added tests inherit its JUnit tests.
## 2026-10-01 — io.opentelemetry:opentelemetry-sdk-trace:1.59.0 (#9350)

**Generated repair retained test-side reflection and an unnecessary Native Image flag**

The copied OpenTelemetrySdkTraceTest directly used Class.forName/getDeclaredField to manufacture reflection evidence, violating FS-test-contract.2.1 and FS-contribution-contract.4.3. The copied build.gradle also retained --allow-incomplete-classpath without evidence that every compliant test shape requires it, violating FS-test-contract.2.7. The complete current-defaults, future-defaults, and GraalVM 25 native lanes passed after both were removed.

## 2026-10-01 — jakarta.websocket:jakarta.websocket-client-api:2.3.0-M1 (#9359)

**Explicit messaging timeouts below the required 10-second floor**

The new test configured WebSocket session idle and asynchronous send timeouts to 250-1000 ms. These explicit client/messaging timeouts violated FS-test-contract.1.7, which requires at least 10 seconds.
## 2026-10-01 — io.micronaut:micronaut-json-core:5.1.3 (#9812)

**Lockstep companion dependency pinned to the requested version**

The coordinate's build.gradle hardcoded io.micronaut:micronaut-jackson-databind:5.1.3 instead of using the TCK-resolved library version. That would leave the supporting mapper implementation pinned when this version-agnostic test is reused, violating the version-pinning rule.

## 2026-09-30 — io.micronaut:micronaut-http-netty:5.1.13 (#9833)

**Version-pinned companion dependencies prevent test reuse**

The test build hardcoded 5.1.13 for four Micronaut companion modules. This violated the version-agnostic test requirement because a later tested version would still execute against stale 5.1.13 server, client, Jackson, and injection modules instead of the TCK-resolved library version.
## 2026-09-30 — io.micronaut.data:micronaut-data-runtime:5.1.3 (#9816)

**Test project declares unused backend dependencies**

FS-test-contract.2.9 permits build.gradle dependency changes only when the dependency is genuinely needed. StaticMetamodelInitializerTest exercises RuntimeCriteriaBuilder and a generated static metamodel but does not use micronaut-data-jdbc, micronaut-jdbc-hikari, H2, or micronaut-test-junit5. All three Native Image finalization lanes passed after those dependencies were removed, confirming they were unnecessary scope.
## 2026-10-01 — org.apache.curator:curator-client:5.0.0 (#9364)

**Zero dynamic-access coverage and unjustified native configuration**

The original 5.0.0 statistics reported 0/123 dynamic-access calls overall, including 0/119 reflection and 0/4 resources, which violated the zero-covered repair gate. The test project also retained --enable-url-protocols=http after the HTTP-based Exhibitor scenarios had been removed, without evidence that any remaining test required the native flag, and Curator client configuration used explicit I/O timeouts below the 10-second minimum.

## 2026-09-25 — org.mongodb:mongodb-driver-core:5.7.0 (#9388)

**Test-side reflection forces an unreachable implementation path**

FS-test-contract.2.1 forbids test-side reflection that bypasses the library behavior a consumer can invoke. tests/src/org.mongodb/mongodb-driver-core/5.7.0/src/test/java/org_mongodb/mongodb_driver_core/DirectBufferDeallocatorInnerJava8DeallocatorTest.java uses getDeclaredClasses, getDeclaredField, getDeclaredMethod, setAccessible, Unsafe.allocateInstance, and direct private-field mutation to manufacture DirectBufferDeallocator$Java8Deallocator execution on the JDK 25 lanes. The normal public constructor selects the Java 9 implementation there. I could not safely rewrite the scenario without that bypass; removing it would delete an inherited passing scenario and may reduce the reported 13/13 dynamic-access coverage. A maintainer must decide how to make or retire this historical coverage without violating the contribution contract.
## 2026-09-30 — org.apache.curator:curator-recipes:5.0.0 (#9365)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.
## 2026-09-30 — io.micronaut.reactor:micronaut-reactor:4.0.0 (#9826)

**Metadata CI used a stale apt index when installing openbsd-inetd**

Workflow run 36703838247 on head 6c0fa721e6586c33399a7b89ed229c10fbf3bd74 failed all three matrix jobs in Disable docker networking. Each job ran sudo apt-get install openbsd-inetd without first refreshing apt indexes, requested libevent-2.1-7t64_2.1.12-stable-9ubuntu2.1, and received 404 Not Found before checkMetadataFiles or the coordinate test task ran. Runs for unrelated contributions failed at the same shared step, and merged PR #10274 repaired .github/workflows/scripts/disable-docker.sh by running apt-get update first. This is outside the contribution's closed file set, so FS-contribution-contract.5.2 and .5.3 prohibit repairing it in this PR and require human intervention.

Infrastructure issue: https://github.com/oracle/graalvm-reachability-metadata/issues/10277 (#10277)

## 2026-09-30 — org.glassfish.ha:ha-api:3.1.13 (#9936)

**Docker isolation CI installs packages with stale APT indexes**

The shared `.github/workflows/scripts/disable-docker.sh` runs `sudo apt-get install openbsd-inetd` without first refreshing APT indexes. In both attempts of current-head run 36685400488, every metadata matrix job requested `libevent-2.1-7t64_2.1.12-stable-9ubuntu2.1_amd64.deb`, received `404 Not Found`, exited 100 in `Disable docker networking`, and skipped metadata validation and the coordinate test. Independent run 36703838247 for `io.micronaut.reactor:micronaut-reactor:4.0.0` later failed all three lanes at the same step with the same package 404, proving the defect is shared rather than specific to `org.glassfish.ha:ha-api:3.1.13`. Under §FS-contribution-contract.5.2–5.3 this must be fixed in shared infrastructure, outside this contribution.

Infrastructure issue: https://github.com/oracle/graalvm-reachability-metadata/issues/10273 (#10273)

## 2026-09-29 — org.apache.tomcat.embed:tomcat-embed-el:10.0.0 (#9371)

**Test-only service provider forced by an unjustified Native Image flag**

The new test project excluded org.apache.el.ExpressionFactoryImpl during Native Image service discovery solely to force a test-only provider, violating FS-test-contract.2.7 because the flag's necessity originated in test configuration rather than the library or its dependencies. The copied test metadata, filter, and new index bucket also retained javax.el entries for the Jakarta-only artifact; those typeReached conditions could never be reached, violating the condition-integrity rule in FS-contribution-contract.4.9.

## 2026-09-30 — org.glassfish.pfl:pfl-tf:5.1.1 (#9944)

**Dynamic-access classes combined in one test file**

The resolved dynamic-access evidence identifies call sites in both MethodMonitorRegistry and EnhancedClassDataReflectiveImpl, but the contribution originally placed both scenarios in MethodMonitorRegistryTest.java. This violated FS-test-contract.1.8, which requires a dedicated test file for each dynamic-access class.

## 2026-09-27 — org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.11 (#10223)

**New-library finalization deletes another coordinate's metadata**

FS-contribution-contract.2 requires this new-library contribution to stay within org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.11 and its supporting files. The reviewed diff deletes metadata/io.grpc/grpc-netty-shaded/1.68.0/reachability-metadata.json in full (git diff --numstat reports 0 additions and 243 deletions). The local gate record confirms human_intervention_required=true and lists that path in repo_fix_paths even though the gate status is success. Under FS-contribution-contract.5.2-.5.3, restoring or otherwise changing another coordinate is not a contribution-local repair, so this branch is blocked on the shared finalization defect.

Infrastructure issue: https://github.com/oracle/graalvm-reachability-metadata/issues/10229 (#10229)
## 2026-09-29 — org.eclipse.jetty:jetty-io:9.4.19.v20190610 (#9377)

**Review finalization hashes unstable abbreviated Git object IDs**

The exact review finalization command repeatedly exits 1 with `Review finalization changed the publishable tree` after every substantive gate passes. `publishable_tree_digest()` hashes raw `git diff --binary HEAD --` output. During `generateLibraryStats`, Git's adaptive object abbreviation grows, changing only the diff header from `index e80d11edc3..92d6c8b6b5` (1918 bytes) to `index e80d11edc34..92d6c8b6b57` (1920 bytes); the patch body and worktree content are unchanged. Because the fix belongs in shared `forge/git_scripts/review_finalization.py`, FS-contribution-contract.5.3 requires escalation rather than a contribution-local repair.

Infrastructure issue: https://github.com/oracle/graalvm-reachability-metadata/issues/10242 (#10242)

## 2026-09-27 — io.lettuce:lettuce-core:6.2.6.RELEASE (#10205)

**Transient CI diagnosis selected unrelated runs**

The transient verdict requested workflow runs that were not failed on the exact reviewed head: 36289510984

## 2026-09-26 — io.lettuce:lettuce-core:6.2.6.RELEASE (#10205)

**Resource-bundle metadata entry missing a reachability condition**

The added sun.util.logging.resources.logging bundle entry had no condition, violating the requirement that every shipped metadata entry be gated by condition.typeReached. Nearby reconnect logging resources were already gated on io.lettuce.core.protocol.ConnectionWatchdog, establishing the valid pre-access condition for the same behavior.

## 2026-09-28 — org.jetbrains.kotlin:kotlin-scripting-jvm:2.4.0 (#9385)

**Native test main-class override bypasses the JUnit suite**

The contribution changed the 2.3.21 baseline outside the single-version file set and configured the 2.4.0 native test binary to run JvmDependencyTest.main instead of the JUnit launcher. That made the native lane execute only a trivial classpath assertion while the Kotlin tests and their metadata remained unverified, violating the closed-file-set and Native Image execution requirements. Removing the override reproduced the concealed evidence: the full native suite found 14 tests and initially failed on missing Kotlin serialization metadata and PathUtil assertions that assumed filesystem-backed class resources. The generated suite also combined the KJvmCompiledScriptKt and KJvmCompiledScript dynamic-access targets in one file and retained test-only/no-op resource metadata.
## 2026-09-28 — org.hibernate.models:hibernate-models:2.0.0.Alpha1 (#9379)

**Test-side reflection bypasses the annotation API**

`AbstractJdkValueExtractorTest` and `OrmAnnotationDescriptorInnerJdkCreatorTest` obtained annotations by reflectively looking up and invoking `AnnotatedElement.getAnnotation`. That unnecessary test-side reflection violated the no-reflection-shortcuts rule in `FS-test-contract.2.1`; the annotation instance can be obtained through the normal public annotation API without changing the tested Hibernate Models behavior.

## 2026-09-28 — org.postgresql:postgresql:42.7.13 (#9394)

**Generated tests used artificial dynamic-access paths and invalid execution bounds**

The generated suite violated test-contract musts: pooled-connection tests directly obtained InvocationHandlers and manually invoked Object.getClass instead of reaching reflection through ordinary library behavior; TimestampUtilsTest forced java.version to Java 8 and required --add-opens to exercise a path unavailable on the resolved JDK; generated test metadata contained unstable lambda class names as typeReached conditions; and several explicit database timeouts were below 10 seconds while Docker process waits were unbounded. These contradicted FS-test-contract.2.1, FS-test-contract.2.8, FS-test-contract.1.7, FS-test-contract.1.6, and the condition-cheating rule in FS-contribution-contract.4.

## 2026-09-25 — org.apache.tomcat.embed:tomcat-embed-core:11.0.18 (#8328)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.

## 2026-09-26 — com.oracle.database.jdbc:ojdbc8:23.26.1.0.0 (#10059)

**Generated library statistics were stale**

The first review-finalization pass regenerated `stats.json`, correcting covered instruction and line counts from 92,681/12,870 to 92,680/12,869. Because the publishable tree changed, that pass exited nonzero as required; the exact finalization command then passed without further changes.
## 2026-09-26 — com.azure:azure-json:1.5.1 (#10056)

**Generated library statistics were stale**

The first exact finalization run regenerated stats/com.azure/azure-json/1.5.1/stats.json and changed the covered instruction, line, and method counts from 10326/2450/486 to 10270/2435/484, then exited nonzero because the publishable tree changed. This contribution-local generated-evidence mismatch was repairable under §FS-contribution-contract.5.1.

## 2026-09-26 — org.jetbrains:annotations:15.0 (#9386)

**Top-level test class was not public**

The new AnnotationsTest was declared package-private in tests/src/org.jetbrains/annotations/15.0/src/test/java/org_jetbrains/annotations/AnnotationsTest.java, while the test contract requires every top-level test class to be public. This was a contribution-local must violation repairable under contribution-contract disposition 5.1.

## 2026-09-25 — org.apache.tomcat.embed:tomcat-embed-core:11.0.22 (#10144)

**Coverage update weakened a baseline logging test and dropped required test metadata**

The contribution removed the formatter setup and assertion from DirectJDKLogTest, weakening an existing passing scenario contrary to FS-test-contract.2.9. It also removed test-only serialization registrations required by unchanged CustomObjectInputStreamTest behavior: finalization reproduced a MissingReflectionRegistrationError for java.util.ArrayList on the latest lane and an UnsupportedFeatureError for java.lang.reflect.Proxy on GraalVM 25. Generated shipped metadata additionally contained test-owned or nonexistent resource entries for TomcatTests$MyServlet, synthetic LocalStrings bundles, and NoSuchRuntimeClass, contrary to the no-test-only-shipped-metadata requirement.

## 2026-09-22 — org.apache.tomcat.embed:tomcat-embed-core:11.0.18 (#8328)

**Transient CI diagnosis selected unrelated runs**

The transient verdict requested workflow runs that were not failed on the exact reviewed head: 35797184278

## 2026-09-22 — org.apache.tomcat.embed:tomcat-embed-core:11.0.18 (#8328)

**Generated metadata includes test-only and machine-local resources**

FS-test-contract.4.4 forbids resource metadata for temporary machine-local paths, and FS-metadata requires test-only resources to stay out of shipped metadata. The generated tree contained five /tmp/junit.../jaas-realm-file.config globs in test-only metadata and nine JUnit, Gradle-worker, or generated test-class resource globs in shipped metadata.

## 2026-09-22 — org.apache.tomcat.embed:tomcat-embed-core:11.0.22 (#10144)

**Transient CI diagnosis selected unrelated runs**

The transient verdict requested workflow runs that were not failed on the exact reviewed head: 35792627095

## 2026-09-22 — org.apache.tomcat.embed:tomcat-embed-core:11.0.22 (#10144)

**Generated coverage removed an existing scenario and retained transient test metadata**

The contribution deleted HostConfigTest.deploysContextDescriptorAtServerStartup, weakening an existing passing scenario contrary to FS-test-contract.2.9; marked WebappClassLoaderBase$PrivilegedJavaseGetResource complete without its required dedicated test file under FS-test-contract.1.8; and retained six HostConfig plus three JAAS /tmp/junit... resource globs, contrary to FS-test-contract.4.4. It also retained an unused RecordingLog helper with 106 unjustified test-only reflection registrations and shipped test-owned or nonexistent resource entries.

## 2026-09-23 — com.azure:azure-json:1.4.0 (#10060)

**New-library dynamic-access coverage is below the required threshold**

The submitted stats reported 0 of 4 dynamic-access calls covered (0%), violating the requirement that a new-library contribution with calls to cover exceed 20% coverage.
## 2026-09-23 — com.oracle.database.jdbc:ojdbc8:23.26.1.0.0 (#10059)

**Completed dynamic-access classes lacked dedicated test files**

The exhaust report marked oracle.jdbc.driver.DMSFactory and oracle.jdbc.driver.GeneratedPhysicalConnection$1 as completed, but the contribution had no dedicated DMSFactoryTest or GeneratedPhysicalConnectionAnonymous1Test. This violated the one-test-file-per-dynamic-access-class requirement in FS-test-contract.1.8.

## 2026-09-15 — com.fasterxml.jackson.jr:jackson-jr-objects:2.21.0 (#8916)

**Test-only metadata shadowed shipped registrations**

The generated test-only reachability metadata duplicated shipped registrations for POJODefinition, array types, LinkedHashMap, and TreeMap behind test-class conditions, so those registrations could satisfy native tests without exercising the shipped library-conditioned entries. It also contained unstable $$Lambda/0x... conditions and nanoTime-derived missing-class targets, which are not stable valid evidence. The contribution additionally changed tracked native test-result XML even though that generated path is repository-ignored.
## 2026-09-20 — org.apache.kafka:kafka-clients:4.2.0 (#7506)

**Coverage improvement removed baseline behavior and counted an asserted failure**

The contribution deleted the existing SCRAM provider behavior test from KafkaClientsTest, contrary to the no-weakening rule, and added reportsUnavailableRawMessageInfoFields, which deliberately supplied a nonexistent protobuf field and asserted the resulting RuntimeException while exercising the reflective call site. That is coverage bought by asserting breakage under FS-contribution-contract.4.4 / FS-test-contract.2.6.
## 2026-09-22 — org.orbisgis:h2gis:2.2.5 (#10139)

**Unrelated KML coverage broadened the H2GIS update**

The generated contribution added STAsKmlTest and ST_AsKml method metadata even though issue #10139 requests the GeoJSON, SRID, geometry-metadata, spatial-predicate, and distance methods. This unrelated feature expansion violated the no-scope-creep requirement in FS-test-contract.2.9.

## 2026-09-21 — org.apache.avro:avro:1.12.2 (#9363)

**Runtime-lambda re-scope left unjustified metadata**

The positive ReflectionUtil.getConstructorAsFunction scenario had been dropped after Native Image runtime lambda definition failed, but the contribution still shipped its manually added lambda registration and retained test-only ConstructedWithString metadata. That left requested metadata without the public-API test required by FS-test-contract.1.5 and FS-test-contract.2.7. The scenario itself is a valid FS-test-contract.4.3.2 re-scope: it concretely uses runtime LambdaMetafactory class definition, Native Image reports UnsupportedFeatureError, Avro catches Throwable and returns null so the refusal cannot be verified, and the remaining public-API tests still pass the repair coverage gate.
## 2026-09-21 — io.micronaut:micronaut-websocket:5.1.15 (#10141)

**Generated test dependencies pin the requested library version**

The coordinate build script hardcoded version 5.1.15 for three Micronaut companion artifacts even though it already derives the tested version as libraryVersion. This violated the version-agnostic test requirement in FS-test-contract.2.5 and Review Signal #4 because the suite would keep those dependencies pinned when reused for a later tested version.
## 2026-09-22 — io.micronaut:micronaut-http-client:5.1.15 (#10140)

**Generated library statistics were stale**

The first Forge finalization pass regenerated stats/io.micronaut/micronaut-http-client/5.1.15/stats.json and changed instruction coverage from 5777 to 5775 covered instructions and line coverage from 1301 to 1300 covered lines. Because the publishable tree changed, finalization correctly exited nonzero and required another pass.
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

## 2026-09-19 — org.springframework.boot:spring-boot-actuator-autoconfigure:4.0.0 (#10003)

**Pre-push review unavailable**

Forge could not obtain a readable pre-push review verdict. This records a review availability problem, not a reviewer finding against the branch.

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
