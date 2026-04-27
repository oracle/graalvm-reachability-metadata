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
    void providerTypeCanMarkTypesUsedByConsumers() {
        ProviderRole providerRole = new DefaultProvider();
        DefaultProvider providerClass = new DefaultProvider();

        assertThat(providerRole.provide()).isEqualTo("provider-response");
        assertThat(providerClass.describe()).isEqualTo("default-provider");
        assertThat(ProviderType.class.getName())
                .isEqualTo("org.osgi.annotation.versioning.ProviderType");
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
