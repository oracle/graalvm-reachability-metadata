/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.base.Equivalence;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.base.Verify;
import com.google.common.base.VerifyException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Text parsing and value conversion with `com.google.common.base`: `Splitter` and `Joiner`
 * (including their map forms), `CharMatcher`, `CaseFormat` and `Enums` converters, `Strings`,
 * `Ascii`, `Preconditions` and `Verify`, `Optional`, `Suppliers`, `Stopwatch` with a `Ticker`,
 * `Equivalence`, `MoreObjects`, and `Throwables`.
 *
 * A deployment tool reads service manifests written as `key: value` lines, normalises the keys
 * to Java field names, parses the enum-valued settings, rejects malformed entries with clear
 * messages, renders a summary for the operator, and times the parse with an injectable clock.
 */
public class DeploymentManifestParsingTest {

    private static final String MANIFEST = """
            # checkout service
            service-name:   checkout-api
            deploy-tier:    production
            replica-count:  3
            exposed-ports:  8080, 8443\s,, 9090
            env-vars:       REGION=eu-west-1;LOG_LEVEL=info ; FEATURE_X = on
            """;

    @Test
    void parsesManifestLinesIntoTypedSettings() {
        Manifest manifest = ManifestParser.parse(MANIFEST);

        assertEquals("checkout-api", manifest.serviceName());
        assertEquals(Tier.PRODUCTION, manifest.tier());
        assertEquals(3, manifest.replicas());
        assertEquals(List.of(8080, 8443, 9090), manifest.ports());
        assertEquals(
                Map.of("REGION", "eu-west-1", "LOG_LEVEL", "info", "FEATURE_X", "on"),
                manifest.environment());
        assertEquals(
                List.of("serviceName", "deployTier", "replicaCount", "exposedPorts", "envVars"),
                List.copyOf(manifest.rawSettings().keySet()));
    }

    @Test
    void rendersOperatorSummaryWithJoiners() {
        Manifest manifest = ManifestParser.parse(MANIFEST);

        String environment = Joiner.on(", ").withKeyValueSeparator("=").join(manifest.environment());
        String ports = Joiner.on('|').skipNulls().join(manifest.ports().get(0), null, manifest.ports().get(2));
        String owners = Joiner.on(", ").useForNull("unassigned").join("payments", null, "sre");

        assertEquals("REGION=eu-west-1, LOG_LEVEL=info, FEATURE_X=on", environment);
        assertEquals("8080|9090", ports);
        assertEquals("payments, unassigned, sre", owners);
        assertEquals(
                "Manifest{service=checkout-api, tier=PRODUCTION, replicas=3}",
                manifest.toString());
        assertEquals("checkout-api....|", Strings.padEnd(manifest.serviceName(), 16, '.') + "|");
        assertEquals("00003", Strings.padStart(String.valueOf(manifest.replicas()), 5, '0'));
        assertEquals("-api", Strings.commonSuffix("checkout-api", "billing-api"));
        assertEquals("=".repeat(12), Strings.repeat("=", 12));
    }

    @Test
    void convertsBetweenNamingConventionsAndEnumNames() {
        Converter<String, String> kebabToCamel = CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_CAMEL);
        Converter<String, Tier> tierNames = Enums.stringConverter(Tier.class);
        Converter<String, Tier> manifestTier = CaseFormat.LOWER_HYPHEN
                .converterTo(CaseFormat.UPPER_UNDERSCORE)
                .andThen(tierNames);

        assertEquals("maxSurgePercent", kebabToCamel.convert("max-surge-percent"));
        assertEquals("max-surge-percent", kebabToCamel.reverse().convert("maxSurgePercent"));
        assertEquals("MAX_SURGE_PERCENT", CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, "maxSurgePercent"));
        assertEquals("MaxSurgePercent", CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, "max_surge_percent"));

        assertEquals(Tier.DISASTER_RECOVERY, manifestTier.convert("disaster-recovery"));
        assertEquals("disaster-recovery", manifestTier.reverse().convert(Tier.DISASTER_RECOVERY));
        assertEquals(
                List.of(Tier.STAGING, Tier.PRODUCTION),
                toList(manifestTier.convertAll(List.of("staging", "production"))));

        assertTrue(Enums.getIfPresent(Tier.class, "STAGING").isPresent());
        assertFalse(Enums.getIfPresent(Tier.class, "QA").isPresent());
        assertEquals(Tier.STAGING, Enums.getIfPresent(Tier.class, "QA").or(Tier.STAGING));
        assertThrows(IllegalArgumentException.class, () -> tierNames.convert("QA"));
    }

    @Test
    void readsEnumConstantAnnotationsThroughTheirFields() {
        Field production = Enums.getField(Tier.PRODUCTION);
        Field staging = Enums.getField(Tier.STAGING);

        assertEquals("PRODUCTION", production.getName());
        Field productionAnnotationAccess = production;
        Field stagingAnnotationAccess = staging;
        assertTrue(productionAnnotationAccess.isAnnotationPresent(RequiresApproval.class));
        assertEquals(2, productionAnnotationAccess.getAnnotation(RequiresApproval.class).approvers());
        assertFalse(stagingAnnotationAccess.isAnnotationPresent(RequiresApproval.class));
    }

    @Test
    void cleansUserSuppliedIdentifiersWithCharMatchers() {
        CharMatcher allowed = CharMatcher.inRange('a', 'z')
                .or(CharMatcher.inRange('0', '9'))
                .or(CharMatcher.is('-'));
        String raw = "  Checkout_API v2!!  ";

        String slug = allowed.negate()
                .collapseFrom(Ascii.toLowerCase(CharMatcher.whitespace().trimFrom(raw)), '-');

        assertEquals("checkout-api-v2-", slug);
        assertEquals("checkout-api-v2", CharMatcher.is('-').trimTrailingFrom(slug));
        assertEquals(3, CharMatcher.inRange('0', '9').countIn("v2.10"));
        assertEquals("v210", CharMatcher.forPredicate(Character::isLetterOrDigit).retainFrom("v2.10"));
        assertTrue(CharMatcher.ascii().matchesAllOf(slug));
        assertEquals(8, CharMatcher.anyOf("-_").indexIn("checkout-api"));
        assertTrue(Ascii.equalsIgnoreCase("CHECKOUT", "checkout"));
        assertEquals("checko...", Ascii.truncate("checkout-api", 9, "..."));
    }

    @Test
    void rejectsMalformedManifestsWithDescriptiveMessages() {
        IllegalArgumentException missingSeparator = assertThrows(
                IllegalArgumentException.class, () -> ManifestParser.parse("service-name checkout"));
        IllegalArgumentException badReplicas = assertThrows(
                IllegalArgumentException.class,
                () -> ManifestParser.parse("service-name: a\ndeploy-tier: staging\nreplica-count: 0"));
        NullPointerException missingTier = assertThrows(
                NullPointerException.class, () -> ManifestParser.parse("service-name: a\nreplica-count: 1"));
        IllegalArgumentException unknownTier = assertThrows(
                IllegalArgumentException.class,
                () -> ManifestParser.parse("service-name: a\ndeploy-tier: qa\nreplica-count: 1"));
        VerifyException duplicatePorts = assertThrows(
                VerifyException.class,
                () -> ManifestParser.parse("""
                        service-name: a
                        deploy-tier: staging
                        replica-count: 1
                        exposed-ports: 80, 80
                        """));
        IllegalStateException unsealed = assertThrows(
                IllegalStateException.class, () -> new ReleaseGate().promote());

        assertEquals("line 1 is not a 'key: value' pair: service-name checkout", missingSeparator.getMessage());
        assertEquals("replica-count must be between 1 and 50 but was 0", badReplicas.getMessage());
        assertEquals("manifest has no deploy-tier", missingTier.getMessage());
        assertEquals("unknown deploy-tier 'qa'", unknownTier.getMessage());
        assertEquals("duplicate exposed port in [80, 80]", duplicatePorts.getMessage());
        assertEquals("release gate is not sealed", unsealed.getMessage());
        assertThrows(IndexOutOfBoundsException.class, () -> Preconditions.checkElementIndex(3, 3, "replica"));
    }

    @Test
    void memoizesAndExpiresExpensiveLookups() {
        AtomicInteger registryCalls = new AtomicInteger();
        FakeTicker ticker = new FakeTicker();
        Supplier<String> registryVersion = () -> "v" + registryCalls.incrementAndGet();

        Supplier<String> memoized = Suppliers.memoize(registryVersion);
        assertEquals("v1", memoized.get());
        assertEquals("v1", memoized.get());

        Supplier<String> expiring = Suppliers.memoizeWithExpiration(registryVersion, 50, TimeUnit.MILLISECONDS);
        assertEquals("v2", expiring.get());
        assertEquals("v2", expiring.get());
        assertEquals(2, registryCalls.get());

        Supplier<Integer> length = Suppliers.compose(String::length, Suppliers.ofInstance("checkout-api"));
        assertEquals(12, length.get());

        Stopwatch stopwatch = Stopwatch.createUnstarted(ticker);
        stopwatch.start();
        ticker.advance(1_250, TimeUnit.MILLISECONDS);
        stopwatch.stop();
        ticker.advance(5, TimeUnit.SECONDS);

        assertFalse(stopwatch.isRunning());
        assertEquals(1_250, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        assertEquals("1.250 s", stopwatch.toString());
        stopwatch.reset().start();
        ticker.advance(40, TimeUnit.MICROSECONDS);
        assertEquals(40, stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Test
    void comparesServiceNamesAndDefaultsMissingValues() {
        Equivalence<String> caseInsensitive = new Equivalence<>() {
            @Override
            protected boolean doEquivalent(String a, String b) {
                return Ascii.equalsIgnoreCase(a, b);
            }

            @Override
            protected int doHash(String name) {
                return Ascii.toLowerCase(name).hashCode();
            }
        };

        assertTrue(caseInsensitive.equivalent("Checkout-API", "checkout-api"));
        assertEquals(caseInsensitive.wrap("Checkout-API"), caseInsensitive.wrap("checkout-api"));
        assertTrue(Equivalence.equals().equivalent(null, null));
        assertTrue(Equivalence.identity().<String>onResultOf(Tier::valueOf).equivalent("STAGING", "STAGING"));
        assertTrue(caseInsensitive.equivalentTo("CHECKOUT").apply("checkout"));

        Optional<String> owner = Optional.fromNullable(null);
        assertEquals("sre", owner.or("sre"));
        assertEquals("PAYMENTS", Optional.of("payments").transform(Ascii::toUpperCase).get());
        assertEquals("x", Optional.of("x").toJavaUtil().orElseThrow());
        assertEquals("fallback", MoreObjects.firstNonNull(null, "fallback"));
        assertEquals("", Strings.nullToEmpty(null));
        assertTrue(Strings.isNullOrEmpty(Strings.emptyToNull("")));
        assertTrue(Predicates.and(Predicates.notNull(), Predicates.containsPattern("^[a-z-]+$")).apply("checkout-api"));
        assertFalse(Predicates.in(List.of("staging")).apply("production"));
    }

    @Test
    void unwrapsDeploymentFailuresForTheOperator() {
        ReleaseGate gate = new ReleaseGate();
        gate.seal();

        RuntimeException failure = assertThrows(RuntimeException.class, gate::promote);

        Throwable root = Throwables.getRootCause(failure);
        assertTrue(root instanceof IOException);
        assertEquals("registry unreachable", root.getMessage());
        assertEquals(3, Throwables.getCausalChain(failure).size());
        assertSame(root, Throwables.getCauseAs(failure.getCause(), IOException.class));
        assertTrue(Throwables.getStackTraceAsString(failure).contains("registry unreachable"));
        assertThrows(UncheckedIOException.class, () -> {
            Throwables.throwIfUnchecked(failure.getCause());
            throw new AssertionError("unchecked cause must be rethrown");
        });
    }

    private static <T> List<T> toList(Iterable<T> values) {
        List<T> list = new ArrayList<>();
        values.forEach(list::add);
        return list;
    }

    /** Marks an environment tier whose deployments need sign-off. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface RequiresApproval {
        int approvers();
    }

    /** Environment tiers a manifest can deploy to. */
    private enum Tier {
        DEVELOPMENT,
        STAGING,
        @RequiresApproval(approvers = 2)
        PRODUCTION,
        @RequiresApproval(approvers = 1)
        DISASTER_RECOVERY
    }

    /** The typed result of parsing a manifest. */
    private record Manifest(
            String serviceName,
            Tier tier,
            int replicas,
            List<Integer> ports,
            Map<String, String> environment,
            Map<String, String> rawSettings) {

        @Override
        public String toString() {
            return MoreObjects.toStringHelper("Manifest")
                    .omitNullValues()
                    .add("service", serviceName)
                    .add("tier", tier)
                    .add("replicas", replicas)
                    .add("owner", null)
                    .toString();
        }
    }

    /** Turns `key: value` manifest text into a [Manifest]. */
    private static final class ManifestParser {

        private static final Splitter LINES = Splitter.on('\n').trimResults().omitEmptyStrings();
        private static final Splitter KEY_VALUE = Splitter.on(':').limit(2).trimResults();
        private static final Splitter PORTS = Splitter.on(',').trimResults().omitEmptyStrings();
        private static final Splitter.MapSplitter ENV = Splitter.on(';')
                .trimResults()
                .withKeyValueSeparator(Splitter.on('=').trimResults());
        private static final Converter<String, String> KEY_NAMES =
                CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.LOWER_CAMEL);

        static Manifest parse(String text) {
            Map<String, String> settings = new LinkedHashMap<>();
            int lineNumber = 0;
            for (String line : LINES.split(text)) {
                lineNumber++;
                if (line.startsWith("#")) {
                    continue;
                }
                List<String> parts = KEY_VALUE.splitToList(line);
                Preconditions.checkArgument(
                        parts.size() == 2, "line %s is not a 'key: value' pair: %s", lineNumber, line);
                settings.put(KEY_NAMES.convert(parts.get(0)), parts.get(1));
            }

            String tierName = Preconditions.checkNotNull(settings.get("deployTier"), "manifest has no deploy-tier");
            Optional<Tier> tier = Enums.getIfPresent(
                    Tier.class, CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, tierName));
            Preconditions.checkArgument(tier.isPresent(), "unknown deploy-tier '%s'", tierName);

            int replicas = Integer.parseInt(settings.getOrDefault("replicaCount", "1"));
            Preconditions.checkArgument(
                    replicas >= 1 && replicas <= 50, "replica-count must be between 1 and 50 but was %s", replicas);

            List<Integer> ports = new ArrayList<>();
            for (String port : PORTS.split(Strings.nullToEmpty(settings.get("exposedPorts")))) {
                ports.add(Integer.valueOf(port));
            }
            Verify.verify(
                    ports.stream().distinct().count() == ports.size(), "duplicate exposed port in %s", ports);

            String env = settings.get("envVars");
            Map<String, String> environment = Strings.isNullOrEmpty(env) ? Map.of() : ENV.split(env);

            return new Manifest(
                    settings.get("serviceName"), tier.get(), replicas, ports, environment, settings);
        }
    }

    /** Promotes a release once it is sealed, wrapping registry failures for the caller. */
    private static final class ReleaseGate {

        private boolean sealed;

        void seal() {
            sealed = true;
        }

        void promote() {
            Preconditions.checkState(sealed, "release gate is not sealed");
            try {
                pushToRegistry();
            } catch (UncheckedIOException e) {
                throw new RuntimeException("promotion failed", e);
            }
        }

        private void pushToRegistry() {
            throw new UncheckedIOException(new IOException("registry unreachable"));
        }
    }

    /** A manually advanced clock so timings are deterministic. */
    private static final class FakeTicker extends Ticker {

        private final AtomicLong nanos = new AtomicLong();

        void advance(long amount, TimeUnit unit) {
            nanos.addAndGet(unit.toNanos(amount));
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }
}
