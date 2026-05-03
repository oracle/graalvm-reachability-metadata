/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.annotations;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.annotations.Generated;
import software.amazon.awssdk.annotations.Immutable;
import software.amazon.awssdk.annotations.Mutable;
import software.amazon.awssdk.annotations.NotNull;
import software.amazon.awssdk.annotations.NotThreadSafe;
import software.amazon.awssdk.annotations.ReviewBeforeRelease;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.annotations.SdkPreviewApi;
import software.amazon.awssdk.annotations.SdkProtectedApi;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.SdkTestInternalApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.annotations.ToBuilderIgnoreField;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsTest {
    @Test
    void sdkAnnotationsCanDocumentPublicImmutableComponents() {
        ImmutableEndpoint endpoint = new ImmutableEndpoint("https://example.amazonaws.com", "us-east-1");

        assertThat(endpoint.endpoint()).isEqualTo("https://example.amazonaws.com");
        assertThat(endpoint.region()).isEqualTo("us-east-1");
        assertThat(endpoint.normalizedEndpoint()).isEqualTo("https://example.amazonaws.com/us-east-1");
        assertThat(endpoint.builderIgnoredFields()).containsExactly("cachedEndpoint", "computedRegion");
    }

    @Test
    void sdkAnnotationsCanDocumentMutablePreviewAndInternalComponents() {
        MutablePreviewRequest request = new MutablePreviewRequest("ListBuckets");

        request.recordAttempt("signed");
        request.recordAttempt("sent");

        assertThat(request.operation()).isEqualTo("ListBuckets");
        assertThat(request.history()).containsExactly("created:ListBuckets", "attempt-1:signed", "attempt-2:sent");
        assertThat(request.describeForTests()).isEqualTo("ListBuckets#2");
    }

    @Test
    void reviewAndGeneratedAnnotationsCanDocumentReleaseSensitiveCode() {
        ReleaseCandidateFeature feature = new ReleaseCandidateFeature("checksum-trailer");

        assertThat(feature.name()).isEqualTo("checksum-trailer");
        assertThat(feature.reviewNote()).isEqualTo("Review generated checksum-trailer before publishing");
        assertThat(feature.generatedDescription("smithy-model")).isEqualTo("smithy-model:checksum-trailer");
    }

    @Test
    void annotationsWithMembersCanBeImplementedWithoutReflection() {
        Generated generated = new Generated() {
            @Override
            public String[] value() {
                return new String[] {"smithy", "test-fixture"};
            }

            @Override
            public String date() {
                return "2026-05-03T00:00:00Z";
            }

            @Override
            public String comments() {
                return "generated for reachability metadata testing";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Generated.class;
            }
        };
        ReviewBeforeRelease reviewBeforeRelease = new ReviewBeforeRelease() {
            @Override
            public String value() {
                return "Confirm preview API status before release";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ReviewBeforeRelease.class;
            }
        };
        ToBuilderIgnoreField toBuilderIgnoreField = new ToBuilderIgnoreField() {
            @Override
            public String[] value() {
                return new String[] {"checksumCache", "clock"};
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ToBuilderIgnoreField.class;
            }
        };

        assertThat(generated.value()).containsExactly("smithy", "test-fixture");
        assertThat(generated.date()).isEqualTo("2026-05-03T00:00:00Z");
        assertThat(generated.comments()).isEqualTo("generated for reachability metadata testing");
        assertThat(generated.annotationType()).isSameAs(Generated.class);
        assertThat(reviewBeforeRelease.value()).isEqualTo("Confirm preview API status before release");
        assertThat(reviewBeforeRelease.annotationType()).isSameAs(ReviewBeforeRelease.class);
        assertThat(toBuilderIgnoreField.value()).containsExactly("checksumCache", "clock");
        assertThat(toBuilderIgnoreField.annotationType()).isSameAs(ToBuilderIgnoreField.class);
    }

    @Test
    void markerAnnotationsCanBeImplementedWithoutReflection() {
        Immutable immutable = new Immutable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Immutable.class;
            }
        };
        Mutable mutable = new Mutable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Mutable.class;
            }
        };
        NotNull notNull = new NotNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NotNull.class;
            }
        };
        NotThreadSafe notThreadSafe = new NotThreadSafe() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NotThreadSafe.class;
            }
        };
        SdkInternalApi sdkInternalApi = new SdkInternalApi() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SdkInternalApi.class;
            }
        };
        SdkPreviewApi sdkPreviewApi = new SdkPreviewApi() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SdkPreviewApi.class;
            }
        };
        SdkProtectedApi sdkProtectedApi = new SdkProtectedApi() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SdkProtectedApi.class;
            }
        };
        SdkPublicApi sdkPublicApi = new SdkPublicApi() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SdkPublicApi.class;
            }
        };
        SdkTestInternalApi sdkTestInternalApi = new SdkTestInternalApi() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SdkTestInternalApi.class;
            }
        };
        ThreadSafe threadSafe = new ThreadSafe() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ThreadSafe.class;
            }
        };

        assertThat(List.of(
                immutable.annotationType(),
                mutable.annotationType(),
                notNull.annotationType(),
                notThreadSafe.annotationType(),
                sdkInternalApi.annotationType(),
                sdkPreviewApi.annotationType(),
                sdkProtectedApi.annotationType(),
                sdkPublicApi.annotationType(),
                sdkTestInternalApi.annotationType(),
                threadSafe.annotationType()))
                .containsExactly(
                        Immutable.class,
                        Mutable.class,
                        NotNull.class,
                        NotThreadSafe.class,
                        SdkInternalApi.class,
                        SdkPreviewApi.class,
                        SdkProtectedApi.class,
                        SdkPublicApi.class,
                        SdkTestInternalApi.class,
                        ThreadSafe.class);
    }

    @NotNull
    @Generated("metadata-contract")
    @interface MetadataContract {
    }

    @Immutable
    @ThreadSafe
    @SdkPublicApi
    @MetadataContract
    static final class ImmutableEndpoint {
        @NotNull
        @SdkPublicApi
        private final String endpoint;

        @NotNull
        private final String region;

        @SdkPublicApi
        ImmutableEndpoint(@NotNull String endpoint, @NotNull String region) {
            this.endpoint = endpoint;
            this.region = region;
        }

        @NotNull
        @SdkPublicApi
        String endpoint() {
            return endpoint;
        }

        @NotNull
        String region() {
            return region;
        }

        @Generated(value = {"smithy", "endpoint-rule-set"}, date = "2026-05-03", comments = "test fixture")
        String normalizedEndpoint() {
            @Generated("normalizer")
            String normalized = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return normalized + "/" + region;
        }

        @ToBuilderIgnoreField({"cachedEndpoint", "computedRegion"})
        List<String> builderIgnoredFields() {
            return List.of("cachedEndpoint", "computedRegion");
        }
    }

    @Mutable
    @NotThreadSafe
    @SdkPreviewApi
    static final class MutablePreviewRequest {
        @SdkProtectedApi
        private final String operation;

        @SdkInternalApi
        private int attempts;

        private final List<String> history = new ArrayList<>();

        @SdkPreviewApi
        MutablePreviewRequest(String operation) {
            this.operation = operation;
            this.history.add("created:" + operation);
        }

        @SdkPreviewApi
        String operation() {
            return operation;
        }

        @SdkInternalApi
        void recordAttempt(String state) {
            attempts++;
            history.add("attempt-" + attempts + ":" + state);
        }

        @SdkProtectedApi
        List<String> history() {
            return List.copyOf(history);
        }

        @SdkTestInternalApi
        String describeForTests() {
            return operation + "#" + attempts;
        }
    }

    @ReviewBeforeRelease("Verify generated code is ready for release")
    @SdkProtectedApi
    static final class ReleaseCandidateFeature {
        @ReviewBeforeRelease("Feature name is surfaced in public generated code")
        private final String name;

        @ReviewBeforeRelease("Constructor accepts release-candidate identifiers")
        ReleaseCandidateFeature(String name) {
            this.name = name;
        }

        @ReviewBeforeRelease("Ensure this feature name is final before GA")
        String name() {
            return name;
        }

        @ReviewBeforeRelease("Release note must be updated if generated text changes")
        String reviewNote() {
            return "Review generated " + name + " before publishing";
        }

        @Generated(value = "smithy-codegen", comments = "release-candidate fixture")
        String generatedDescription(String generator) {
            return generator + ":" + name;
        }
    }
}
