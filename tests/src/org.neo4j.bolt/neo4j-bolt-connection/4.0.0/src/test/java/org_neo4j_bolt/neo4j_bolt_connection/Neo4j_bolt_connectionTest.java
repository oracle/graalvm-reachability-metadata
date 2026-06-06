/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_bolt.neo4j_bolt_connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;
import org.neo4j.bolt.connection.AuthToken;
import org.neo4j.bolt.connection.AuthTokens;
import org.neo4j.bolt.connection.BasicResponseHandler;
import org.neo4j.bolt.connection.BoltAgent;
import org.neo4j.bolt.connection.BoltProtocolVersion;
import org.neo4j.bolt.connection.BoltServerAddress;
import org.neo4j.bolt.connection.ClusterComposition;
import org.neo4j.bolt.connection.DatabaseName;
import org.neo4j.bolt.connection.DatabaseNameUtil;
import org.neo4j.bolt.connection.DefaultDomainNameResolver;
import org.neo4j.bolt.connection.GqlStatusError;
import org.neo4j.bolt.connection.NotificationClassification;
import org.neo4j.bolt.connection.NotificationConfig;
import org.neo4j.bolt.connection.NotificationSeverity;
import org.neo4j.bolt.connection.RoutingContext;
import org.neo4j.bolt.connection.SecurityPlan;
import org.neo4j.bolt.connection.SecurityPlans;
import org.neo4j.bolt.connection.TelemetryApi;
import org.neo4j.bolt.connection.exception.BoltClientException;
import org.neo4j.bolt.connection.exception.BoltConnectionReadTimeoutException;
import org.neo4j.bolt.connection.exception.BoltFailureException;
import org.neo4j.bolt.connection.exception.BoltGqlErrorException;
import org.neo4j.bolt.connection.exception.BoltProtocolException;
import org.neo4j.bolt.connection.exception.MinVersionAcquisitionException;
import org.neo4j.bolt.connection.ssl.RevocationCheckingStrategy;
import org.neo4j.bolt.connection.ssl.SSLContexts;
import org.neo4j.bolt.connection.ssl.TrustManagerFactories;
import org.neo4j.bolt.connection.summary.BeginSummary;
import org.neo4j.bolt.connection.summary.CommitSummary;
import org.neo4j.bolt.connection.summary.DiscardSummary;
import org.neo4j.bolt.connection.summary.LogoffSummary;
import org.neo4j.bolt.connection.summary.LogonSummary;
import org.neo4j.bolt.connection.summary.PullSummary;
import org.neo4j.bolt.connection.summary.ResetSummary;
import org.neo4j.bolt.connection.summary.RollbackSummary;
import org.neo4j.bolt.connection.summary.RouteSummary;
import org.neo4j.bolt.connection.summary.RunSummary;
import org.neo4j.bolt.connection.summary.TelemetrySummary;
import org.neo4j.bolt.connection.values.IsoDuration;
import org.neo4j.bolt.connection.values.Node;
import org.neo4j.bolt.connection.values.Path;
import org.neo4j.bolt.connection.values.Point;
import org.neo4j.bolt.connection.values.Relationship;
import org.neo4j.bolt.connection.values.Segment;
import org.neo4j.bolt.connection.values.Type;
import org.neo4j.bolt.connection.values.Value;
import org.neo4j.bolt.connection.values.ValueFactory;

public class Neo4j_bolt_connectionTest {
    private static final SimpleValueFactory valueFactory = new SimpleValueFactory();

    @Test
    void authTokenFactoriesCreateExpectedImmutableTokenMaps() {
        AuthToken basic = AuthTokens.basic("neo4j", "secret", "native", valueFactory);

        assertTokenEntries(basic, Map.of(
                "scheme", "basic",
                "principal", "neo4j",
                "credentials", "secret",
                "realm", "native"));
        assertThatThrownBy(() -> basic.asMap().put("extra", valueFactory.value("value")))
                .isInstanceOf(UnsupportedOperationException.class);

        assertTokenEntries(AuthTokens.bearer("token-value", valueFactory), Map.of(
                "scheme", "bearer",
                "credentials", "token-value"));
        assertTokenEntries(AuthTokens.kerberos("ticket", valueFactory), Map.of(
                "scheme", "kerberos",
                "principal", "",
                "credentials", "ticket"));
        assertTokenEntries(AuthTokens.none(valueFactory), Map.of("scheme", "none"));

        Map<String, Value> customValues = new LinkedHashMap<>();
        customValues.put("scheme", valueFactory.value("custom"));
        customValues.put("parameters", valueFactory.value(Map.of("region", valueFactory.value("eu"))));
        AuthToken custom = AuthTokens.custom(customValues);

        assertThat(custom.asMap().get("scheme").asString()).isEqualTo("custom");
        assertThat(custom.asMap().get("parameters").asMap(Value::asString)).containsEntry("region", "eu");
        assertThatThrownBy(() -> custom.asMap().remove("scheme"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void valueFactoryDefaultMethodsCreateTypedBoltValues() {
        assertThat(valueFactory.value(true).type()).isEqualTo(Type.BOOLEAN);
        assertThat(valueFactory.value(true).asBoolean()).isTrue();
        assertThat(valueFactory.value(42L).type()).isEqualTo(Type.INTEGER);
        assertThat(valueFactory.value(42L).asLong()).isEqualTo(42L);
        assertThat(valueFactory.value(1.25D).type()).isEqualTo(Type.FLOAT);
        assertThat(valueFactory.value(1.25D).asDouble()).isEqualTo(1.25D);
        assertThat(valueFactory.value(new byte[] {1, 2, 3}).asByteArray())
                .containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(valueFactory.value("text").asString()).isEqualTo("text");

        Value mapped = valueFactory.value(Map.of("answer", valueFactory.value(42L)));
        assertThat(mapped.type()).isEqualTo(Type.MAP);
        assertThat(mapped.containsKey("answer")).isTrue();
        assertThat(mapped.asMap(Value::asLong)).containsEntry("answer", 42L);

        Value listed = valueFactory.value(new Value[] {valueFactory.value("a"), valueFactory.value("b")});
        assertThat(listed.type()).isEqualTo(Type.LIST);
        assertThat(listed.size()).isEqualTo(2);
        assertThat(listed.values()).extracting(Value::asString).containsExactly("a", "b");

        LocalDate date = LocalDate.of(2026, 5, 4);
        LocalTime time = LocalTime.of(12, 30, 15);
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        OffsetTime offsetTime = OffsetTime.of(time, ZoneOffset.UTC);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(dateTime, ZoneOffset.UTC);

        assertThat(valueFactory.value(date).asLocalDate()).isEqualTo(date);
        assertThat(valueFactory.value(offsetTime).asOffsetTime()).isEqualTo(offsetTime);
        assertThat(valueFactory.value(time).asLocalTime()).isEqualTo(time);
        assertThat(valueFactory.value(dateTime).asLocalDateTime()).isEqualTo(dateTime);
        assertThat(valueFactory.value(OffsetDateTime.of(dateTime, ZoneOffset.UTC)).asZonedDateTime())
                .isEqualTo(zonedDateTime);
        assertThat(valueFactory.value(zonedDateTime).asZonedDateTime()).isEqualTo(zonedDateTime);

        IsoDuration period = valueFactory.value(Period.of(1, 2, 3)).asIsoDuration();
        assertThat(period.months()).isEqualTo(14L);
        assertThat(period.days()).isEqualTo(3L);
        IsoDuration duration = valueFactory.value(Duration.ofSeconds(5, 6)).asIsoDuration();
        assertThat(duration.seconds()).isEqualTo(5L);
        assertThat(duration.nanoseconds()).isEqualTo(6);

        Point point = valueFactory.point(4326, 1.0D, 2.0D, 3.0D).asPoint();
        assertThat(point.srid()).isEqualTo(4326);
        assertThat(point.z()).isEqualTo(3.0D);
        assertThat(valueFactory.unsupportedDateTimeValue(new DateTimeException("invalid")).isNull())
                .isTrue();
    }

    @Test
    void addressesRoutingContextsAndDatabaseNamesFollowBoltUriRules() {
        BoltServerAddress defaultPortAddress = new BoltServerAddress("neo4j.example.com");
        assertThat(defaultPortAddress.host()).isEqualTo("neo4j.example.com");
        assertThat(defaultPortAddress.connectionHost()).isEqualTo("neo4j.example.com");
        assertThat(defaultPortAddress.port()).isEqualTo(BoltServerAddress.DEFAULT_PORT);
        assertThat(defaultPortAddress.toString()).isEqualTo("neo4j.example.com:7687");
        assertThat(defaultPortAddress.unicastStream()).containsExactly(defaultPortAddress);

        BoltServerAddress routedAddress = new BoltServerAddress("public.example.com", "private.example.com", 9000);
        assertThat(routedAddress.host()).isEqualTo("public.example.com");
        assertThat(routedAddress.connectionHost()).isEqualTo("private.example.com");
        assertThat(routedAddress.toString()).isEqualTo("public.example.com(private.example.com):9000");
        assertThat(new BoltServerAddress(URI.create("bolt://db.example.com:9999")))
                .isEqualTo(new BoltServerAddress("db.example.com", 9999));
        assertThatThrownBy(() -> new BoltServerAddress("db.example.com", -1))
                .isInstanceOf(IllegalArgumentException.class);

        RoutingContext empty = RoutingContext.EMPTY;
        assertThat(empty.isDefined()).isFalse();
        assertThat(empty.isServerRoutingEnabled()).isTrue();
        assertThat(empty.toMap()).isEmpty();

        RoutingContext routing = new RoutingContext(
                URI.create("neo4j://router.example.com:9000?region=eu&policy=fast"));
        assertThat(routing.isDefined()).isTrue();
        assertThat(routing.isServerRoutingEnabled()).isTrue();
        assertThat(routing.toMap()).containsEntry("address", "router.example.com:9000")
                .containsEntry("region", "eu")
                .containsEntry("policy", "fast");
        assertThatThrownBy(() -> routing.toMap().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        RoutingContext direct = new RoutingContext(URI.create("bolt://db.example.com?policy=fast"));
        assertThat(direct.isServerRoutingEnabled()).isFalse();
        assertThat(direct.toMap()).containsEntry("address", "db.example.com:7687");
        assertThatThrownBy(() -> new RoutingContext(URI.create("neo4j://router.example.com?address=elsewhere")))
                .isInstanceOf(IllegalArgumentException.class);

        DatabaseName defaultDatabase = DatabaseNameUtil.defaultDatabase();
        assertThat(defaultDatabase.databaseName()).isEmpty();
        assertThat(defaultDatabase.description()).isEqualTo("<default database>");
        assertThat(DatabaseNameUtil.database(null)).isSameAs(defaultDatabase);
        assertThat(DatabaseNameUtil.systemDatabase().databaseName()).contains(DatabaseNameUtil.SYSTEM_DATABASE_NAME);
        assertThat(DatabaseNameUtil.database("customers").databaseName()).contains("customers");
    }

    @Test
    void defaultDomainNameResolverResolvesLiteralAddresses() throws Exception {
        DefaultDomainNameResolver resolver = DefaultDomainNameResolver.getInstance();

        InetAddress[] addresses = resolver.resolve("127.0.0.1");

        assertThat(DefaultDomainNameResolver.getInstance()).isSameAs(resolver);
        assertThat(addresses).hasSize(1);
        assertThat(addresses[0].isLoopbackAddress()).isTrue();
    }

    @Test
    void protocolVersionsNotificationsClusterAndSecurityPlansExposeValueObjects() throws Exception {
        BoltProtocolVersion min = new BoltProtocolVersion(5, 4);
        BoltProtocolVersion max = new BoltProtocolVersion(5, 8);

        assertThat(min.toInt()).isEqualTo(0x0405);
        assertThat(BoltProtocolVersion.fromRawBytes(0x0405)).isEqualTo(min);
        assertThat(max.toIntRange(min)).isEqualTo(0x00040805);
        assertThat(max).isGreaterThan(min);
        assertThat(max.toString()).isEqualTo("5.8");
        assertThat(BoltProtocolVersion.isHttp(new BoltProtocolVersion(80, 84))).isTrue();
        assertThatThrownBy(() -> min.toIntRange(max)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> max.toIntRange(new BoltProtocolVersion(4, 4)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(NotificationSeverity.valueOf("WARNING")).contains(NotificationSeverity.WARNING);
        assertThat(NotificationSeverity.valueOf("UNKNOWN")).isEmpty();
        assertThat(NotificationSeverity.WARNING).isGreaterThan(NotificationSeverity.INFORMATION);
        NotificationClassification performance = new NotificationClassification(
                NotificationClassification.Type.PERFORMANCE);
        assertThat(NotificationClassification.valueOf("PERFORMANCE")).contains(performance);
        assertThat(NotificationClassification.valueOf("MISSING")).isEmpty();
        NotificationConfig defaultConfig = NotificationConfig.defaultConfig();
        assertThat(defaultConfig.minimumSeverity()).isNull();
        assertThat(defaultConfig.disabledClassifications()).isNull();
        assertThat(new NotificationConfig(NotificationSeverity.WARNING, Set.of(performance)).disabledClassifications())
                .containsExactly(performance);

        BoltServerAddress reader = new BoltServerAddress("reader.example.com", 7687);
        BoltServerAddress writer = new BoltServerAddress("writer.example.com", 7687);
        BoltServerAddress router = new BoltServerAddress("router.example.com", 7687);
        ClusterComposition cluster = new ClusterComposition(
                123L, Set.of(reader), Set.of(writer), Set.of(router), "customers");
        assertThat(cluster.hasWriters()).isTrue();
        assertThat(cluster.hasRoutersAndReaders()).isTrue();
        assertThat(cluster.readers()).containsExactly(reader);
        assertThat(cluster.writers()).containsExactly(writer);
        assertThat(cluster.routers()).containsExactly(router);
        assertThat(cluster.expirationTimestamp()).isEqualTo(123L);
        assertThat(cluster.databaseName()).isEqualTo("customers");
        Set<BoltServerAddress> returnedReaders = cluster.readers();
        returnedReaders.clear();
        assertThat(cluster.readers()).containsExactly(reader);

        assertThat(TelemetryApi.MANAGED_TRANSACTION.getValue()).isZero();
        assertThat(TelemetryApi.EXECUTABLE_QUERY.getValue()).isEqualTo(3);
        assertThat(GqlStatusError.UNKNOWN.getStatus()).isEqualTo("50N42");
        assertThat(GqlStatusError.UNKNOWN.getStatusDescription("details")).contains("details");

        SSLContext sslContext = SSLContexts.forAnyCertificate(null);
        SecurityPlan encrypted = SecurityPlans.encrypted(true, sslContext, true);
        assertThat(encrypted.requiresEncryption()).isTrue();
        assertThat(encrypted.requiresClientAuth()).isTrue();
        assertThat(encrypted.sslContext()).isSameAs(sslContext);
        assertThat(encrypted.requiresHostnameVerification()).isTrue();
        SecurityPlan trustAll = SecurityPlans.encryptedForAnyCertificate();
        assertThat(trustAll.requiresEncryption()).isTrue();
        assertThat(trustAll.requiresHostnameVerification()).isFalse();
        SecurityPlan unencrypted = SecurityPlans.unencrypted();
        assertThat(unencrypted.requiresEncryption()).isFalse();
        assertThat(unencrypted.sslContext()).isNull();
    }

    @Test
    void trustManagerFactoriesLoadCustomCertificateAuthorities() throws Exception {
        String certificatePem = """
                -----BEGIN CERTIFICATE-----
                MIIDKzCCAhOgAwIBAgIUGsyFet7bJQ6KSUw7HZdF5rkpjAQwDQYJKoZIhvcNAQEL
                BQAwJTEjMCEGA1UEAwwabmVvNGotYm9sdC1jb25uZWN0aW9uLXRlc3QwHhcNMjYw
                NTAzMjMxMjU1WhcNMzYwNDMwMjMxMjU1WjAlMSMwIQYDVQQDDBpuZW80ai1ib2x0
                LWNvbm5lY3Rpb24tdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB
                AN3z6Hu5f8wMJ4BD87Tcvzqrhf5MEwoeZiHF4ad3O2oxDxhekCszwIcunSZuI8jy
                ZyQ5/CZCl7bYiZIPIXUOvM19/BstekKMTb1ExsagHjow+BuX4Rd5bHaHxdFKTil1
                G30LLJoL6HzwQPCQSe7Vi6Yx03Gc+HKIcDx6K0VuYnHUltvVVFmsrN+DTZAwd7lf
                bYS71oBBP6ryyJFVLAfdwnRgBDLaCjjR+lLk2bE2pDnGTNVxzcRcK4V7cBUbnbE7
                5cNMMydcKWD77tmvdkGLGstRkYENLvN2ARmCirrDbxvmKxm459Th/Mtd+Ijh8vpr
                Nq6igWTYF94ZnxyablXmFmMCAwEAAaNTMFEwHQYDVR0OBBYEFDbgBGqdLYFKgPun
                4WwGIQVpzEGJMB8GA1UdIwQYMBaAFDbgBGqdLYFKgPun4WwGIQVpzEGJMA8GA1Ud
                EwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAAYl0rKViicYuieyYHqHf2Aj
                p7UPJEvjeiCQmadclOMPkeZowhn8hgWSC8jnPivSLqA+G0UKRl68wKQZdQvTB8pw
                mrUO9J4X+VIPbw1cVrIoQ27okBNqiDXI8R0tJcHvOi9KxvXEv2/qxZN5lX7s6EFv
                LEQwH7ODHoItbovuzOtXTQ5pgb11oFZcIjgQqWBEYHzhA2FmVMBrILNWTUf89tn4
                o3y/5hZ1nQFUZwZmQTJFA9fTeopQJX85y0OoLA+YGwpLUToniQD+ylyjOw/J0nlE
                MFpIGAupm6xVOrbHPoidV1uaEX7YoaDUYq5FEqh1Fclbl/BIOsbmtUIO7rT+BF8=
                -----END CERTIFICATE-----
                """;
        java.nio.file.Path certificateFile = Files.createTempFile("neo4j-bolt-connection-ca", ".pem");
        try {
            Files.writeString(certificateFile, certificatePem);

            TrustManagerFactory factory = TrustManagerFactories.forCertificates(
                    List.of(certificateFile.toFile()), RevocationCheckingStrategy.NO_CHECKS);
            Optional<X509TrustManager> trustManager = Arrays.stream(factory.getTrustManagers())
                    .filter(candidate -> candidate instanceof X509TrustManager)
                    .map(candidate -> (X509TrustManager) candidate)
                    .findFirst();

            assertThat(trustManager).isPresent();
            assertThat(Arrays.stream(trustManager.orElseThrow().getAcceptedIssuers())
                            .map(certificate -> certificate.getSubjectX500Principal().getName())
                            .toList())
                    .anyMatch(subject -> subject.contains("CN=neo4j-bolt-connection-test"));
        } finally {
            Files.deleteIfExists(certificateFile);
        }
    }

    @Test
    void basicResponseHandlerCollectsSummariesRecordsAndIgnoredMessages() throws Exception {
        BasicResponseHandler handler = new BasicResponseHandler();
        BeginSummary beginSummary = new TestBeginSummary("customers");
        RunSummary runSummary = new TestRunSummary(7L, List.of("name", "age"), 12L, "customers");
        Map<String, Value> metadata = Map.of("bookmark", valueFactory.value("bm-1"));
        PullSummary pullSummary = new TestPullSummary(true, metadata);
        DiscardSummary discardSummary = new TestDiscardSummary(metadata);
        CommitSummary commitSummary = new TestCommitSummary("bm-2");
        ClusterComposition cluster = new ClusterComposition(
                123L,
                Set.of(new BoltServerAddress("reader.example.com", 7687)),
                Set.of(new BoltServerAddress("writer.example.com", 7687)),
                Set.of(new BoltServerAddress("router.example.com", 7687)),
                "customers");
        TestRouteSummary routeSummary = new TestRouteSummary(cluster);
        Value[] record = new Value[] {valueFactory.value("Alice"), valueFactory.value(42L)};

        handler.onBeginSummary(beginSummary);
        handler.onRunSummary(runSummary);
        handler.onRecord(record);
        handler.onPullSummary(pullSummary);
        handler.onDiscardSummary(discardSummary);
        handler.onCommitSummary(commitSummary);
        handler.onRollbackSummary(new TestRollbackSummary());
        handler.onResetSummary(new TestResetSummary());
        handler.onRouteSummary(routeSummary);
        handler.onLogoffSummary(new TestLogoffSummary());
        handler.onLogonSummary(new TestLogonSummary());
        handler.onTelemetrySummary(new TestTelemetrySummary());
        handler.onIgnored();
        handler.onIgnored();
        handler.onComplete();

        BasicResponseHandler.Summaries summaries = handler.summaries().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertThat(summaries.beginSummary()).isSameAs(beginSummary);
        assertThat(summaries.runSummary().queryId()).isEqualTo(7L);
        assertThat(summaries.runSummary().keys()).containsExactly("name", "age");
        assertThat(summaries.runSummary().databaseName()).contains("customers");
        assertThat(summaries.valuesList()).containsExactly(record);
        assertThat(summaries.pullSummary().hasMore()).isTrue();
        assertThat(summaries.pullSummary().metadata()).containsEntry("bookmark", valueFactory.value("bm-1"));
        assertThat(summaries.discardSummary().metadata()).containsEntry("bookmark", valueFactory.value("bm-1"));
        assertThat(summaries.commitSummary().bookmark()).contains("bm-2");
        assertThat(summaries.routeSummary().clusterComposition()).isSameAs(cluster);
        assertThat(summaries.logoffSummary()).isNotNull();
        assertThat(summaries.logonSummary()).isNotNull();
        assertThat(summaries.telemetrySummary()).isNotNull();
        assertThat(summaries.ignored()).isEqualTo(2);
    }

    @Test
    void basicResponseHandlerCompletesExceptionallyWithMostUsefulError() {
        BasicResponseHandler handler = new BasicResponseHandler();
        BoltProtocolException protocolError = new BoltProtocolException("protocol failed");
        IllegalStateException stateError = new IllegalStateException("decoder failed");

        handler.onError(protocolError);
        handler.onError(new CompletionException(stateError));
        handler.onComplete();

        assertThatThrownBy(() -> handler.summaries().toCompletableFuture().get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCause(stateError);
    }

    @Test
    void exceptionTypesPreserveBoltAndGqlMetadata() {
        BoltProtocolVersion requiredVersion = new BoltProtocolVersion(5, 7);
        MinVersionAcquisitionException minVersionException = new MinVersionAcquisitionException(
                "server is too old", requiredVersion);
        assertThat(minVersionException.version()).isSameAs(requiredVersion);
        assertThat(minVersionException).hasMessage("server is too old");

        Map<String, Value> diagnostics = Map.of("operation", valueFactory.value("run"));
        BoltGqlErrorException gqlCause = new BoltGqlErrorException(
                "50N42", "cause description", "cause message", diagnostics, null);
        BoltFailureException failure = new BoltFailureException(
                "Neo.ClientError.Statement.SyntaxError",
                "invalid query",
                "42001",
                "syntax error",
                diagnostics,
                new BoltClientException("client", gqlCause));

        assertThat(failure.code()).isEqualTo("Neo.ClientError.Statement.SyntaxError");
        assertThat(failure.gqlStatus()).isEqualTo("42001");
        assertThat(failure.statusDescription()).isEqualTo("syntax error");
        assertThat(failure).hasMessage("invalid query");
        assertThat(failure.diagnosticRecord()).containsEntry("operation", valueFactory.value("run"));
        assertThat(failure.gqlCause()).contains(gqlCause);

        BoltConnectionReadTimeoutException timeout = new BoltConnectionReadTimeoutException(
                "read timed out", failure);
        assertThat(timeout).hasMessage("read timed out").hasCause(failure);
        assertThat(new BoltAgent("neo4j-bolt-connection-test", "JVM", "Java", "JUnit").product())
                .isEqualTo("neo4j-bolt-connection-test");
    }

    private static void assertTokenEntries(AuthToken token, Map<String, String> expectedEntries) {
        assertThat(token.asMap()).hasSize(expectedEntries.size());
        expectedEntries.forEach((key, value) -> assertThat(token.asMap().get(key).asString()).isEqualTo(value));
    }

    private record TestBeginSummary(String databaseNameValue) implements BeginSummary {
        @Override
        public Optional<String> databaseName() {
            return Optional.ofNullable(databaseNameValue);
        }
    }

    private record TestRunSummary(long queryId, List<String> keys, long resultAvailableAfter, String databaseNameValue)
            implements RunSummary {
        @Override
        public Optional<String> databaseName() {
            return Optional.ofNullable(databaseNameValue);
        }
    }

    private record TestPullSummary(boolean hasMore, Map<String, Value> metadata) implements PullSummary {
    }

    private record TestDiscardSummary(Map<String, Value> metadata) implements DiscardSummary {
    }

    private record TestCommitSummary(String bookmarkValue) implements CommitSummary {
        @Override
        public Optional<String> bookmark() {
            return Optional.ofNullable(bookmarkValue);
        }
    }

    private record TestRouteSummary(ClusterComposition clusterComposition) implements RouteSummary {
    }

    private record TestRollbackSummary() implements RollbackSummary {
    }

    private record TestResetSummary() implements ResetSummary {
    }

    private record TestLogoffSummary() implements LogoffSummary {
    }

    private record TestLogonSummary() implements LogonSummary {
    }

    private record TestTelemetrySummary() implements TelemetrySummary {
    }

    private static final class SimpleValueFactory implements ValueFactory {
        @Override
        public Value value(Object value) {
            if (value == null) {
                return new SimpleValue(Type.NULL, null);
            }
            if (value instanceof Value boltValue) {
                return boltValue;
            }
            if (value instanceof Boolean booleanValue) {
                return new SimpleValue(Type.BOOLEAN, booleanValue);
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return new SimpleValue(Type.INTEGER, ((Number) value).longValue());
            }
            if (value instanceof Float || value instanceof Double) {
                return new SimpleValue(Type.FLOAT, ((Number) value).doubleValue());
            }
            if (value instanceof byte[] bytes) {
                return new SimpleValue(Type.BYTES, bytes.clone());
            }
            if (value instanceof String stringValue) {
                return new SimpleValue(Type.STRING, stringValue);
            }
            if (value instanceof Map<?, ?> mapValue) {
                return new SimpleValue(Type.MAP, new LinkedHashMap<>(castValueMap(mapValue)));
            }
            if (value instanceof Value[] values) {
                return new SimpleValue(Type.LIST, List.of(values));
            }
            if (value instanceof LocalDate || value instanceof OffsetTime || value instanceof LocalTime
                    || value instanceof LocalDateTime || value instanceof ZonedDateTime) {
                return new SimpleValue(typeForTemporal(value), value);
            }
            if (value instanceof OffsetDateTime offsetDateTime) {
                return new SimpleValue(Type.DATE_TIME, offsetDateTime.toZonedDateTime());
            }
            if (value instanceof Period period) {
                return new SimpleValue(Type.DURATION, new SimpleIsoDuration(
                        period.toTotalMonths(), period.getDays(), 0L, 0));
            }
            if (value instanceof Duration duration) {
                return new SimpleValue(Type.DURATION, new SimpleIsoDuration(
                        0L, 0L, duration.getSeconds(), duration.getNano()));
            }
            if (value instanceof Node) {
                return new SimpleValue(Type.NODE, value);
            }
            if (value instanceof Relationship) {
                return new SimpleValue(Type.RELATIONSHIP, value);
            }
            if (value instanceof Path) {
                return new SimpleValue(Type.PATH, value);
            }
            throw new IllegalArgumentException("Unsupported value type: " + value.getClass().getName());
        }

        @Override
        public Node node(long id, String elementId, Collection<String> labels, Map<String, Value> properties) {
            return new SimpleNode(id, elementId, List.copyOf(labels), Map.copyOf(properties));
        }

        @Override
        public Relationship relationship(
                long id,
                String elementId,
                long startNodeId,
                String startNodeElementId,
                long endNodeId,
                String endNodeElementId,
                String type,
                Map<String, Value> properties) {
            SimpleRelationship relationship = new SimpleRelationship(id, elementId, type, Map.copyOf(properties));
            relationship.setStartAndEnd(startNodeId, startNodeElementId, endNodeId, endNodeElementId);
            return relationship;
        }

        @Override
        public Segment segment(Node start, Relationship relationship, Node end) {
            return new SimpleSegment(start, relationship, end);
        }

        @Override
        public Path path(List<Segment> segments, List<Node> nodes, List<Relationship> relationships) {
            return new SimplePath(List.copyOf(segments), List.copyOf(nodes), List.copyOf(relationships));
        }

        @Override
        public Value isoDuration(long months, long days, long seconds, int nanoseconds) {
            return new SimpleValue(Type.DURATION, new SimpleIsoDuration(months, days, seconds, nanoseconds));
        }

        @Override
        public Value point(int srid, double x, double y) {
            return new SimpleValue(Type.POINT, new SimplePoint(srid, x, y, Double.NaN));
        }

        @Override
        public Value point(int srid, double x, double y, double z) {
            return new SimpleValue(Type.POINT, new SimplePoint(srid, x, y, z));
        }

        @Override
        public Value unsupportedDateTimeValue(DateTimeException e) {
            return new SimpleValue(Type.NULL, e);
        }

        private static Type typeForTemporal(Object value) {
            if (value instanceof LocalDate) {
                return Type.DATE;
            }
            if (value instanceof OffsetTime) {
                return Type.TIME;
            }
            if (value instanceof LocalTime) {
                return Type.LOCAL_TIME;
            }
            if (value instanceof LocalDateTime) {
                return Type.LOCAL_DATE_TIME;
            }
            return Type.DATE_TIME;
        }

        private static Map<String, Value> castValueMap(Map<?, ?> mapValue) {
            Map<String, Value> values = new LinkedHashMap<>();
            mapValue.forEach((key, value) -> values.put((String) key, (Value) value));
            return values;
        }
    }

    private record SimpleValue(Type type, Object rawValue) implements Value {
        @Override
        public boolean asBoolean() {
            return (Boolean) rawValue;
        }

        @Override
        public byte[] asByteArray() {
            return ((byte[]) rawValue).clone();
        }

        @Override
        public String asString() {
            return (String) rawValue;
        }

        @Override
        public long asLong() {
            return (Long) rawValue;
        }

        @Override
        public double asDouble() {
            return (Double) rawValue;
        }

        @Override
        public LocalDate asLocalDate() {
            return (LocalDate) rawValue;
        }

        @Override
        public OffsetTime asOffsetTime() {
            return (OffsetTime) rawValue;
        }

        @Override
        public LocalTime asLocalTime() {
            return (LocalTime) rawValue;
        }

        @Override
        public LocalDateTime asLocalDateTime() {
            return (LocalDateTime) rawValue;
        }

        @Override
        public ZonedDateTime asZonedDateTime() {
            return (ZonedDateTime) rawValue;
        }

        @Override
        public IsoDuration asIsoDuration() {
            return (IsoDuration) rawValue;
        }

        @Override
        public Point asPoint() {
            return (Point) rawValue;
        }

        @Override
        public boolean isNull() {
            return type == Type.NULL;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public Iterable<String> keys() {
            return asMapValue().keySet();
        }

        @Override
        public int size() {
            if (type == Type.MAP) {
                return asMapValue().size();
            }
            if (type == Type.LIST) {
                return asListValue().size();
            }
            return 0;
        }

        @Override
        public Value get(String key) {
            return asMapValue().get(key);
        }

        @Override
        public Iterable<Value> values() {
            if (type == Type.LIST) {
                return asListValue();
            }
            return asMapValue().values();
        }

        @Override
        public boolean containsKey(String key) {
            return asMapValue().containsKey(key);
        }

        @Override
        public <T> Map<String, T> asMap(Function<Value, T> mapFunction) {
            Map<String, T> mapped = new LinkedHashMap<>();
            asMapValue().forEach((key, value) -> mapped.put(key, mapFunction.apply(value)));
            return mapped;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Value> asMapValue() {
            if (type != Type.MAP) {
                return Collections.emptyMap();
            }
            return (Map<String, Value>) rawValue;
        }

        @SuppressWarnings("unchecked")
        private List<Value> asListValue() {
            if (type != Type.LIST) {
                return List.of();
            }
            return (List<Value>) rawValue;
        }
    }

    private record SimpleIsoDuration(long months, long days, long seconds, int nanoseconds) implements IsoDuration {
    }

    private record SimplePoint(int srid, double x, double y, double z) implements Point {
    }

    private record SimpleNode(long id, String elementId, List<String> labels, Map<String, Value> properties)
            implements Node {
    }

    private static final class SimpleRelationship implements Relationship {
        private final long id;
        private final String elementId;
        private final String type;
        private final Map<String, Value> properties;
        private long startNodeId;
        private String startNodeElementId;
        private long endNodeId;
        private String endNodeElementId;

        private SimpleRelationship(long id, String elementId, String type, Map<String, Value> properties) {
            this.id = id;
            this.elementId = elementId;
            this.type = type;
            this.properties = properties;
        }

        @Override
        public void setStartAndEnd(long start, String startElementId, long end, String endElementId) {
            this.startNodeId = start;
            this.startNodeElementId = startElementId;
            this.endNodeId = end;
            this.endNodeElementId = endElementId;
        }
    }

    private record SimpleSegment(Node start, Relationship relationship, Node end) implements Segment {
    }

    private record SimplePath(
            List<Segment> segments,
            List<Node> nodes,
            List<Relationship> relationships) implements Path {
    }
}
