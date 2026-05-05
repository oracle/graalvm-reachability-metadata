/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_annotations;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Hadoop_annotationsTest {
    @Test
    void publicStableApiAnnotationsCanMarkACompatibleClientContract() {
        PublicCatalog catalog = new PublicCatalog();

        catalog.put("/warehouse/events", "stable");
        catalog.put("/warehouse/users", "stable");

        assertThat(catalog.lookup("/warehouse/events")).isEqualTo("stable");
        assertThat(catalog.paths()).containsExactly("/warehouse/events", "/warehouse/users");
        assertThat(catalog.describe()).isEqualTo("public-stable entries=2");
    }

    @Test
    void limitedPrivateEvolvingAnnotationsModelSubsystemSpecificExtensionPoints() {
        SubsystemMetrics metrics = new SubsystemMetrics("HDFS", "MapReduce");

        metrics.record("HDFS", 3);
        metrics.record("MapReduce", 5);
        metrics.record("YARN", 7);

        assertThat(metrics.supportedSubsystems()).containsExactly("HDFS", "MapReduce");
        assertThat(metrics.totalForSupportedSubsystems()).isEqualTo(8);
        assertThat(metrics.describeAudience()).isEqualTo("limited-private:HDFS,MapReduce");
    }

    @Test
    void privateUnstableAnnotationsCanMarkImplementationDetails() {
        RollingChecksum checksum = new RollingChecksum();

        checksum.update("alpha");
        checksum.update("beta");

        assertThat(checksum.digest()).isEqualTo("alpha|beta");
        assertThat(checksum.resetAndReport()).isEqualTo("reset 2 fragments");
        assertThat(checksum.digest()).isEmpty();
    }

    @Test
    void markerAnnotationsCanBeAppliedToSeveralDeclarationKinds() {
        AnnotatedDeclarations declarations = new AnnotatedDeclarations("ready");

        assertThat(declarations.combine("client", 2)).isEqualTo("ready:client:2");
        assertThat(declarations.transition(AnnotatedLifecycle.STARTED)).isEqualTo("ready->STARTED");
        assertThat(declarations.convertWithLocalDeclaration("done")).isEqualTo("READY-done");
        assertThat(new PublicRecord("nn", 8020).endpoint()).isEqualTo("nn:8020");
    }

    @Test
    void annotationInterfacesCanBeUsedThroughTheirPublicAnnotationContracts() {
        InterfaceAudience.Public publicAudience = new PublicAudienceLiteral();
        InterfaceAudience.Private privateAudience = new PrivateAudienceLiteral();
        InterfaceAudience.LimitedPrivate limitedAudience = new LimitedPrivateAudienceLiteral("HDFS", "YARN");
        InterfaceStability.Stable stable = new StableLiteral();
        InterfaceStability.Evolving evolving = new EvolvingLiteral();
        InterfaceStability.Unstable unstable = new UnstableLiteral();

        assertThat(publicAudience.annotationType()).isSameAs(InterfaceAudience.Public.class);
        assertThat(privateAudience.annotationType()).isSameAs(InterfaceAudience.Private.class);
        assertThat(limitedAudience.annotationType()).isSameAs(InterfaceAudience.LimitedPrivate.class);
        assertThat(limitedAudience.value()).containsExactly("HDFS", "YARN");
        assertThat(stable.annotationType()).isSameAs(InterfaceStability.Stable.class);
        assertThat(evolving.annotationType()).isSameAs(InterfaceStability.Evolving.class);
        assertThat(unstable.annotationType()).isSameAs(InterfaceStability.Unstable.class);
    }

    @Test
    void limitedPrivateLiteralFollowsAnnotationValueEqualityRules() {
        InterfaceAudience.LimitedPrivate hdfsAndYarn = new LimitedPrivateAudienceLiteral("HDFS", "YARN");
        InterfaceAudience.LimitedPrivate sameAudience = new LimitedPrivateAudienceLiteral("HDFS", "YARN");
        InterfaceAudience.LimitedPrivate hdfsOnly = new LimitedPrivateAudienceLiteral("HDFS");

        assertThat(hdfsAndYarn).isEqualTo(sameAudience);
        assertThat(hdfsAndYarn.hashCode()).isEqualTo(sameAudience.hashCode());
        assertThat(hdfsAndYarn).isNotEqualTo(hdfsOnly);
        assertThat(hdfsAndYarn.toString())
                .isEqualTo("@org.apache.hadoop.classification.InterfaceAudience$LimitedPrivate(value=[HDFS, YARN])");
    }

    @InterfaceAudience.Public
    @InterfaceStability.Stable
    private interface CatalogApi {
        void put(String path, String state);

        List<String> paths();

        String lookup(String path);
    }

    @InterfaceAudience.Public
    @InterfaceStability.Stable
    private static final class PublicCatalog implements CatalogApi {
        private final List<CatalogEntry> entries = new ArrayList<>();

        @Override
        public void put(String path, String state) {
            entries.add(new CatalogEntry(path, state));
        }

        @Override
        public List<String> paths() {
            return entries.stream().map(CatalogEntry::path).toList();
        }

        @Override
        public String lookup(String path) {
            return entries.stream()
                    .filter(entry -> entry.path().equals(path))
                    .findFirst()
                    .map(CatalogEntry::state)
                    .orElse("missing");
        }

        private String describe() {
            return "public-stable entries=" + entries.size();
        }
    }

    @InterfaceAudience.Public
    @InterfaceStability.Stable
    private record CatalogEntry(String path, String state) {
    }

    @InterfaceAudience.LimitedPrivate({"HDFS", "MapReduce"})
    @InterfaceStability.Evolving
    private static final class SubsystemMetrics {
        private final List<String> supportedSubsystems;
        private final List<MetricValue> values = new ArrayList<>();

        private SubsystemMetrics(String... supportedSubsystems) {
            this.supportedSubsystems = List.of(supportedSubsystems);
        }

        private void record(String subsystem, int value) {
            values.add(new MetricValue(subsystem, value));
        }

        private List<String> supportedSubsystems() {
            return supportedSubsystems;
        }

        private int totalForSupportedSubsystems() {
            return values.stream()
                    .filter(metricValue -> supportedSubsystems.contains(metricValue.subsystem()))
                    .mapToInt(MetricValue::value)
                    .sum();
        }

        private String describeAudience() {
            return "limited-private:" + String.join(",", supportedSubsystems);
        }
    }

    @InterfaceAudience.LimitedPrivate({"HDFS", "MapReduce"})
    @InterfaceStability.Evolving
    private record MetricValue(String subsystem, int value) {
    }

    @InterfaceAudience.Private
    @InterfaceStability.Unstable
    private static final class RollingChecksum {
        private final List<String> fragments = new ArrayList<>();

        private void update(String fragment) {
            fragments.add(fragment);
        }

        private String digest() {
            return String.join("|", fragments);
        }

        private String resetAndReport() {
            int fragmentCount = fragments.size();
            fragments.clear();
            return "reset " + fragmentCount + " fragments";
        }
    }

    @InterfaceAudience.Public
    private enum AnnotatedLifecycle {
        STARTED,
        STOPPED
    }

    @InterfaceAudience.Public
    private @interface PublicMarker {
        String value();
    }

    @InterfaceAudience.Public
    @InterfaceStability.Evolving
    private record PublicRecord(String host, int port) {
        private String endpoint() {
            return host + ":" + port;
        }
    }

    private static final class AnnotatedDeclarations {
        @InterfaceAudience.Private
        private final String state;

        @InterfaceAudience.LimitedPrivate("Tests")
        private AnnotatedDeclarations(String state) {
            this.state = state;
        }

        @InterfaceAudience.Public
        @InterfaceStability.Stable
        private String combine(@InterfaceAudience.Public String client, @InterfaceStability.Evolving int attempt) {
            return state + ":" + client + ":" + attempt;
        }

        @PublicMarker("lifecycle")
        private String transition(AnnotatedLifecycle lifecycle) {
            return state + "->" + lifecycle.name();
        }

        private String convertWithLocalDeclaration(String suffix) {
            @InterfaceAudience.Private
            String normalized = state.toUpperCase();
            return normalized + "-" + suffix;
        }
    }

    private static final class PublicAudienceLiteral implements InterfaceAudience.Public {
        @Override
        public Class<? extends Annotation> annotationType() {
            return InterfaceAudience.Public.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InterfaceAudience.Public;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class PrivateAudienceLiteral implements InterfaceAudience.Private {
        @Override
        public Class<? extends Annotation> annotationType() {
            return InterfaceAudience.Private.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InterfaceAudience.Private;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class LimitedPrivateAudienceLiteral implements InterfaceAudience.LimitedPrivate {
        private final String[] value;

        private LimitedPrivateAudienceLiteral(String... value) {
            this.value = Arrays.copyOf(value, value.length);
        }

        @Override
        public String[] value() {
            return Arrays.copyOf(value, value.length);
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return InterfaceAudience.LimitedPrivate.class;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InterfaceAudience.LimitedPrivate otherAudience)) {
                return false;
            }
            return Arrays.equals(value, otherAudience.value());
        }

        @Override
        public int hashCode() {
            return (127 * "value".hashCode()) ^ Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "@" + InterfaceAudience.LimitedPrivate.class.getName() + "(value=" + Arrays.toString(value) + ")";
        }
    }

    private static final class StableLiteral implements InterfaceStability.Stable {
        @Override
        public Class<? extends Annotation> annotationType() {
            return InterfaceStability.Stable.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InterfaceStability.Stable;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class EvolvingLiteral implements InterfaceStability.Evolving {
        @Override
        public Class<? extends Annotation> annotationType() {
            return InterfaceStability.Evolving.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InterfaceStability.Evolving;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class UnstableLiteral implements InterfaceStability.Unstable {
        @Override
        public Class<? extends Annotation> annotationType() {
            return InterfaceStability.Unstable.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InterfaceStability.Unstable;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
