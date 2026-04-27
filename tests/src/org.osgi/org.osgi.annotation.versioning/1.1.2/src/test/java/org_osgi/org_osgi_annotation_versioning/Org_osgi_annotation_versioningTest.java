/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_annotation_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;
import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.annotation.versioning.Version;

public class Org_osgi_annotation_versioningTest {
    @Test
    void consumerTypeCanMarkInterfacesImplementedByConsumers() {
        ConsumerRole consumer = new ConsumerRoleImplementation();

        assertThat(consumer.format("request")).isEqualTo("consumer:request");
        assertThat(ConsumerType.class.getName())
                .isEqualTo("org.osgi.annotation.versioning.ConsumerType");
    }

    @Test
    void consumerTypeCanMarkAbstractClassesExtendedByConsumers() {
        ConsumerBase consumer = new ConsumerBaseImplementation();

        assertThat(consumer.format("request")).isEqualTo("abstract-consumer:request");
    }

    @Test
    void providerTypeCanMarkTypesUsedByConsumers() {
        ProviderRole providerRole = new DefaultProvider();
        DefaultProvider providerClass = new DefaultProvider();

        assertThat(providerRole.provide()).isEqualTo("provider-response");
        assertThat(providerClass.describe()).isEqualTo("default-provider");
        assertThat(ProviderType.class.getName())
                .isEqualTo("org.osgi.annotation.versioning.ProviderType");
    }

    @Test
    void providerTypeCanMarkAbstractClassesExtendedByProviders() {
        ProviderTemplate provider = new GeneratedProvider();

        assertThat(provider.provide("request"))
                .isEqualTo("provider:request");
        assertThat(provider.describeProvider())
                .isEqualTo("generated-provider");
    }

    @Test
    void versionAnnotationExposesPackageVersionValueContract() {
        Version version = new FixedVersion("1.2.3.osgi");

        assertThat(version.annotationType()).isSameAs(Version.class);
        assertThat(version.value()).isEqualTo("1.2.3.osgi");
        assertThat(Version.class.getName())
                .isEqualTo("org.osgi.annotation.versioning.Version");
    }

    @Test
    void markerAnnotationsExposeTheirAnnotationTypes() {
        Annotation consumerType = new ConsumerTypeMarker();
        Annotation providerType = new ProviderTypeMarker();

        assertThat(consumerType.annotationType()).isSameAs(ConsumerType.class);
        assertThat(providerType.annotationType()).isSameAs(ProviderType.class);
    }

    @ConsumerType
    private interface ConsumerRole {
        String format(String value);
    }

    private static final class ConsumerRoleImplementation implements ConsumerRole {
        @Override
        public String format(String value) {
            return "consumer:" + value;
        }
    }

    @ConsumerType
    private abstract static class ConsumerBase {
        final String format(String value) {
            return prefix() + ":" + value;
        }

        abstract String prefix();
    }

    private static final class ConsumerBaseImplementation extends ConsumerBase {
        @Override
        String prefix() {
            return "abstract-consumer";
        }
    }

    @ProviderType
    private interface ProviderRole {
        String provide();
    }

    @ProviderType
    private static final class DefaultProvider implements ProviderRole {
        @Override
        public String provide() {
            return "provider-response";
        }

        String describe() {
            return "default-provider";
        }
    }

    @ProviderType
    private abstract static class ProviderTemplate {
        final String provide(String value) {
            return providerPrefix() + ":" + value;
        }

        abstract String providerPrefix();

        abstract String describeProvider();
    }

    private static final class GeneratedProvider extends ProviderTemplate {
        @Override
        String providerPrefix() {
            return "provider";
        }

        @Override
        String describeProvider() {
            return "generated-provider";
        }
    }

    private static final class FixedVersion implements Version {
        private final String value;

        FixedVersion(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Version.class;
        }
    }

    private static final class ConsumerTypeMarker implements ConsumerType {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ConsumerType.class;
        }
    }

    private static final class ProviderTypeMarker implements ProviderType {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ProviderType.class;
        }
    }
}
