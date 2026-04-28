/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apiguardian.apiguardian_api;

import java.lang.annotation.Annotation;
import java.util.List;

import org.apiguardian.api.API;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Apiguardian_apiTest {
    @Test
    void statusEnumDefinesApiLifecycleInDeclarationOrder() {
        List<API.Status> statuses = List.of(API.Status.values());

        assertThat(statuses)
                .containsExactly(
                        API.Status.INTERNAL,
                        API.Status.DEPRECATED,
                        API.Status.EXPERIMENTAL,
                        API.Status.MAINTAINED,
                        API.Status.STABLE);
        assertThat(statuses)
                .extracting(API.Status::name)
                .containsExactly("INTERNAL", "DEPRECATED", "EXPERIMENTAL", "MAINTAINED", "STABLE");
        assertThat(statuses)
                .extracting(API.Status::ordinal)
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void statusEnumRoundTripsByNameAndStringRepresentation() {
        for (API.Status status : API.Status.values()) {
            assertThat(API.Status.valueOf(status.name())).isSameAs(status);
            assertThat(status.toString()).isEqualTo(status.name());
        }

        assertThat(API.Status.INTERNAL.compareTo(API.Status.DEPRECATED)).isNegative();
        assertThat(API.Status.STABLE.compareTo(API.Status.MAINTAINED)).isPositive();
    }

    @Test
    void statusValuesReturnDefensiveCopies() {
        API.Status[] mutableStatuses = API.Status.values();
        mutableStatuses[0] = API.Status.STABLE;

        assertThat(API.Status.values())
                .containsExactly(
                        API.Status.INTERNAL,
                        API.Status.DEPRECATED,
                        API.Status.EXPERIMENTAL,
                        API.Status.MAINTAINED,
                        API.Status.STABLE);
    }

    @Test
    void statusValueOfRejectsUnknownOrNullNames() {
        assertThatThrownBy(() -> API.Status.valueOf("internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal");
        assertThatThrownBy(() -> API.Status.valueOf("BETA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BETA");
        assertThatThrownBy(() -> API.Status.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void manualAnnotationImplementationsExposeConfiguredMembers() {
        API internal = api(API.Status.INTERNAL, "0.1", "maintainers");
        API stable = api(API.Status.STABLE, "1.1.2", "library-authors", "tooling");
        API deprecated = api(API.Status.DEPRECATED, "2.0");

        assertThat(internal.status()).isSameAs(API.Status.INTERNAL);
        assertThat(internal.since()).isEqualTo("0.1");
        assertThat(internal.consumers()).containsExactly("maintainers");
        assertThat(internal.annotationType()).isSameAs(API.class);

        assertThat(stable.status()).isSameAs(API.Status.STABLE);
        assertThat(stable.since()).isEqualTo("1.1.2");
        assertThat(stable.consumers()).containsExactly("library-authors", "tooling");
        assertThat(stable.annotationType()).isSameAs(API.class);

        assertThat(deprecated.status()).isSameAs(API.Status.DEPRECATED);
        assertThat(deprecated.since()).isEqualTo("2.0");
        assertThat(deprecated.consumers()).isEmpty();
        assertThat(deprecated.annotationType()).isSameAs(API.class);
    }

    @Test
    void apiAnnotationCanMarkClassesConstructorsFieldsMethodsAndAnnotationTypes() {
        AnnotatedLifecycleCatalog catalog = new AnnotatedLifecycleCatalog("apiguardian");

        assertThat(catalog.describe()).isEqualTo("apiguardian:stable-api");
        assertThat(catalog.normalize("  Native Image  ")).isEqualTo("native image");
        assertThat(catalog.legacyAlias()).isEqualTo("API Guardian");
        assertThat(new DefaultedApiUse().describe()).isEqualTo("default-members-compile");
    }

    @Test
    void apiAnnotationExposesDefaultMembersAtRuntime() {
        API annotation = DefaultAudienceApi.class.getAnnotation(API.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.status()).isSameAs(API.Status.MAINTAINED);
        assertThat(annotation.since()).isEmpty();
        assertThat(annotation.consumers()).containsExactly("*");
        assertThat(annotation.annotationType()).isSameAs(API.class);
    }

    @Test
    void apiAnnotationRetainsConfiguredInterfaceMetadataAtRuntime() {
        API annotation = PublishedExtensionPoint.class.getAnnotation(API.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.status()).isSameAs(API.Status.EXPERIMENTAL);
        assertThat(annotation.since()).isEqualTo("initial-public-contract");
        assertThat(annotation.consumers()).containsExactly("library-authors", "extension-authors");
        assertThat(annotation.annotationType()).isSameAs(API.class);

        String[] consumers = annotation.consumers();
        consumers[0] = "changed";

        assertThat(annotation.consumers()).containsExactly("library-authors", "extension-authors");
    }

    private static API api(API.Status status, String since, String... consumers) {
        return new API() {
            @Override
            public API.Status status() {
                return status;
            }

            @Override
            public String since() {
                return since;
            }

            @Override
            public String[] consumers() {
                return consumers;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return API.class;
            }
        };
    }

    @API(status = API.Status.INTERNAL, since = "0.1", consumers = {"maintainers", "test-tools"})
    private static final class AnnotatedLifecycleCatalog {
        @API(status = API.Status.DEPRECATED, since = "1.0", consumers = "legacy-integrations")
        private final String legacyName;

        private final String libraryName;

        @API(status = API.Status.EXPERIMENTAL, since = "1.1", consumers = "native-image-tests")
        private AnnotatedLifecycleCatalog(String libraryName) {
            this.libraryName = libraryName;
            this.legacyName = "API Guardian";
        }

        @API(status = API.Status.MAINTAINED, since = "1.1.2", consumers = "applications")
        private String describe() {
            return libraryName + ":stable-api";
        }

        @API(status = API.Status.STABLE, since = "1.1.2", consumers = {"libraries", "frameworks"})
        private String normalize(String value) {
            return value.trim().toLowerCase();
        }

        private String legacyAlias() {
            return legacyName;
        }
    }

    @API(status = API.Status.STABLE)
    private static final class DefaultedApiUse {
        private String describe() {
            return "default-members-compile";
        }
    }

    @API(status = API.Status.MAINTAINED)
    private static final class DefaultAudienceApi {
    }

    @API(status = API.Status.EXPERIMENTAL, since = "initial-public-contract", consumers = {
            "library-authors", "extension-authors"
    })
    private interface PublishedExtensionPoint {
        String configure(String option);
    }

    @API(status = API.Status.STABLE, since = "1.1.2", consumers = "annotation-authors")
    private @interface StableApiContract {
    }

    @StableApiContract
    private interface ContractConsumer {
    }
}
