/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
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

public class AnnotationsTest {
    @Test
    void annotatedPublicApiTypeBehavesLikeRegularJavaCode() {
        ServiceModel model = new ServiceModel("request-id", "checksum");

        assertThat(model.id()).isEqualTo("request-id");
        assertThat(model.checksum()).isEqualTo("checksum");
        assertThat(model.operationName("ListBuckets")).isEqualTo("aws:ListBuckets");
    }

    @Test
    void generatedAndNullabilityAnnotationsCanBeUsedOnSupportedProgramElements() {
        GeneratedClient client = new GeneratedClient("s3");

        assertThat(client.service()).isEqualTo("s3");
        assertThat(client.endpointFor("us-east-1")).isEqualTo("https://s3.us-east-1.amazonaws.com");
    }

    @Test
    void notNullCanBeComposedIntoReusableRecordComponentContracts() {
        AccountIdentifier identifier = new AccountIdentifier(" 123456789012 ");

        assertThat(identifier.value()).isEqualTo("123456789012");
        assertThat(identifier.qualifiedBy("aws")).isEqualTo("aws:123456789012");
    }

    @Test
    void mutabilityAndThreadSafetyAnnotationsCanDescribeImplementationClasses() {
        MutableCounter mutableCounter = new MutableCounter();
        mutableCounter.increment();
        mutableCounter.increment();

        assertThat(mutableCounter.count()).isEqualTo(2);
        assertThat(ThreadSafeCache.key("profile", "region")).isEqualTo("profile:region");
        assertThat(new NotThreadSafeBuilder().value("payload").build()).isEqualTo("payload");
    }

    @Test
    void sdkLifecycleAnnotationsCanBeAppliedToTypesMembersConstructorsAndMethods() {
        LifecycleCoverage coverage = new LifecycleCoverage("input");

        assertThat(coverage.publicValue).isEqualTo("public");
        assertThat(coverage.protectedValue).isEqualTo("protected");
        assertThat(coverage.previewValue).isEqualTo("preview");
        assertThat(coverage.internalValue).isEqualTo("internal");
        assertThat(coverage.testInternalValue).isEqualTo("test-internal");
        assertThat(coverage.reviewValue).isEqualTo("review");
        assertThat(coverage.combine("output")).isEqualTo("input/output");
    }

    @Test
    void toBuilderIgnoreFieldCanMarkDerivedAccessorMethods() {
        BuilderBackedModel model = new BuilderBackedModel("orders", "checksum");

        assertThat(model.service()).isEqualTo("orders");
        assertThat(model.transientMetadata()).isEqualTo("orders:checksum");
    }

    @Test
    void sdkAnnotationsCanBeComposedIntoReusableTypeStereotypes() {
        StableEndpoint endpoint = new StableEndpoint("s3", "us-west-2");

        assertThat(endpoint.host()).isEqualTo("s3.us-west-2.amazonaws.com");
        assertThat(endpoint.cacheKey()).isEqualTo("s3#us-west-2");
    }

    @SdkPublicApi
    @Immutable
    private static final class ServiceModel {
        @SdkProtectedApi
        private final String id;
        private final String checksum;

        @SdkPublicApi
        private ServiceModel(@NotNull String id, @NotNull String checksum) {
            this.id = id;
            this.checksum = checksum;
        }

        @SdkPublicApi
        @NotNull
        private String id() {
            return id;
        }

        @SdkProtectedApi
        @NotNull
        private String checksum() {
            return checksum;
        }

        @SdkPreviewApi
        private String operationName(@NotNull String operation) {
            return "aws:" + operation;
        }
    }

    @Generated(value = "aws-sdk-code-generator", date = "2026-01-01", comments = "covers generated annotation members")
    private static final class GeneratedClient {
        @Generated("field-generator")
        @NotNull
        private final String service;

        @Generated(value = "constructor-generator", comments = "constructor target")
        private GeneratedClient(@Generated("parameter-generator") @NotNull String service) {
            this.service = service;
        }

        @Generated(value = "method-generator", date = "2026-01-02")
        @NotNull
        private String service() {
            @Generated("local-variable-generator")
            String normalized = service.trim();
            return normalized;
        }

        private String endpointFor(@NotNull String region) {
            return "https://" + service + "." + region + ".amazonaws.com";
        }
    }

    @Generated("annotation-type-generator")
    @NotNull
    private @interface NotNullByDefault {
    }

    @NotNull
    @Target({ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.PARAMETER})
    private @interface RequiredText {
    }

    @SdkPublicApi
    @Immutable
    private record AccountIdentifier(@RequiredText String value) {
        private AccountIdentifier {
            value = value.trim();
        }

        private String qualifiedBy(@RequiredText String partition) {
            return partition + ':' + value;
        }
    }

    @SdkPublicApi
    @Immutable
    @ThreadSafe
    @Target(ElementType.TYPE)
    private @interface StableSdkType {
    }

    @StableSdkType
    private static final class StableEndpoint {
        private final String service;
        private final String region;

        private StableEndpoint(String service, String region) {
            this.service = service;
            this.region = region;
        }

        private String host() {
            return service + '.' + region + ".amazonaws.com";
        }

        private String cacheKey() {
            return service + '#' + region;
        }
    }

    @Mutable
    private static final class MutableCounter {
        private int count;

        private void increment() {
            count++;
        }

        private int count() {
            return count;
        }
    }

    @ThreadSafe
    private static final class ThreadSafeCache {
        private ThreadSafeCache() {
        }

        private static String key(String left, String right) {
            return left + ':' + right;
        }
    }

    @NotThreadSafe
    private static final class NotThreadSafeBuilder {
        private String value;

        private NotThreadSafeBuilder value(String value) {
            this.value = value;
            return this;
        }

        private String build() {
            return value;
        }
    }

    @SdkPublicApi
    @SdkProtectedApi
    @SdkPreviewApi
    @SdkInternalApi
    @SdkTestInternalApi
    @ReviewBeforeRelease("Verify API lifecycle before releasing this test fixture.")
    private static final class LifecycleCoverage {
        @SdkPublicApi
        private final String publicValue = "public";
        @SdkProtectedApi
        private final String protectedValue = "protected";
        @SdkPreviewApi
        private final String previewValue = "preview";
        @SdkInternalApi
        private final String internalValue = "internal";
        @SdkTestInternalApi
        private final String testInternalValue = "test-internal";
        @ReviewBeforeRelease("Confirm this field remains review-only.")
        private final String reviewValue = "review";
        private final String constructorValue;

        @SdkInternalApi
        @SdkTestInternalApi
        @ReviewBeforeRelease("Confirm constructor visibility before release.")
        private LifecycleCoverage(String constructorValue) {
            this.constructorValue = constructorValue;
        }

        @SdkPublicApi
        @SdkProtectedApi
        @SdkPreviewApi
        @SdkInternalApi
        @SdkTestInternalApi
        @ReviewBeforeRelease("Confirm method lifecycle before release.")
        private String combine(String methodValue) {
            return constructorValue + '/' + methodValue;
        }
    }

    private static final class BuilderBackedModel {
        private final String service;
        private final String checksum;

        private BuilderBackedModel(String service, String checksum) {
            this.service = service;
            this.checksum = checksum;
        }

        private String service() {
            return service;
        }

        @ToBuilderIgnoreField({"transientMetadata", "derivedChecksum"})
        private String transientMetadata() {
            return service + ':' + checksum;
        }
    }
}
