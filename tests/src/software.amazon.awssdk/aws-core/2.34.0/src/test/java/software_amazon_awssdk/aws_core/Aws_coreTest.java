/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.aws_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsExecutionAttribute;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.AwsResponseMetadata;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.awscore.auth.AuthSchemePreferenceResolver;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.awscore.endpoint.AwsClientEndpointProvider;
import software.amazon.awssdk.awscore.endpoint.DualstackEnabledProvider;
import software.amazon.awssdk.awscore.endpoint.FipsEnabledProvider;
import software.amazon.awssdk.awscore.endpoints.AccountIdEndpointMode;
import software.amazon.awssdk.awscore.endpoints.AccountIdEndpointModeResolver;
import software.amazon.awssdk.awscore.endpoints.authscheme.EndpointAuthScheme;
import software.amazon.awssdk.awscore.endpoints.authscheme.SigV4AuthScheme;
import software.amazon.awssdk.awscore.endpoints.authscheme.SigV4aAuthScheme;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.awscore.presigner.PresignRequest;
import software.amazon.awssdk.awscore.presigner.PresignedRequest;
import software.amazon.awssdk.awscore.retry.conditions.RetryOnErrorCodeCondition;
import software.amazon.awssdk.awscore.util.AwsHeader;
import software.amazon.awssdk.awscore.util.AwsHostNameUtils;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkRequestOverrideConfiguration;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;
import software.amazon.awssdk.identity.spi.TokenIdentity;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;

public class Aws_coreTest {
    @Test
    void requestOverrideConfigurationKeepsAwsIdentityProvidersAndCommonOverrides() {
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("access", "secret"));
        IdentityProvider<TokenIdentity> tokenProvider = new StaticTokenProvider("token-value");

        AwsRequestOverrideConfiguration configuration = AwsRequestOverrideConfiguration.builder()
                .credentialsProvider(credentialsProvider)
                .tokenIdentityProvider(tokenProvider)
                .putHeader("X-Amz-Test", "one")
                .putRawQueryParameter("marker", "first")
                .apiCallTimeout(Duration.ofSeconds(3))
                .apiCallAttemptTimeout(Duration.ofSeconds(1))
                .putExecutionAttribute(AwsExecutionAttribute.AWS_REGION, Region.US_WEST_2)
                .build();

        assertThat(configuration.credentialsProvider()).contains(credentialsProvider);
        assertThat(configuration.credentialsIdentityProvider()).contains(credentialsProvider);
        assertThat(configuration.tokenIdentityProvider()).contains(tokenProvider);
        assertThat(configuration.headers()).containsEntry("X-Amz-Test", List.of("one"));
        assertThat(configuration.rawQueryParameters()).containsEntry("marker", List.of("first"));
        assertThat(configuration.apiCallTimeout()).contains(Duration.ofSeconds(3));
        assertThat(configuration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(1));
        assertThat(configuration.executionAttributes().getAttribute(AwsExecutionAttribute.AWS_REGION))
                .isEqualTo(Region.US_WEST_2);

        AwsRequestOverrideConfiguration rebuilt = configuration.toBuilder()
                .putHeader("X-Amz-Test", "two")
                .build();
        assertThat(rebuilt.credentialsProvider()).contains(credentialsProvider);
        assertThat(rebuilt.tokenIdentityProvider()).contains(tokenProvider);
        assertThat(rebuilt.headers()).containsEntry("X-Amz-Test", List.of("two"));
        assertThat(rebuilt).isNotEqualTo(configuration);
    }

    @Test
    void awsRequestOverrideConfigurationCanBeCreatedFromCoreOverrideConfiguration() {
        SdkRequestOverrideConfiguration coreConfiguration = SdkRequestOverrideConfiguration.builder()
                .putHeader("X-Header", List.of("a", "b"))
                .putRawQueryParameter("page", "1")
                .apiCallTimeout(Duration.ofMillis(250))
                .addApiName(apiName -> apiName.name("integration-tests").version("1"))
                .build();

        AwsRequestOverrideConfiguration converted = AwsRequestOverrideConfiguration.from(coreConfiguration);

        assertThat(converted.headers()).containsEntry("X-Header", List.of("a", "b"));
        assertThat(converted.rawQueryParameters()).containsEntry("page", List.of("1"));
        assertThat(converted.apiCallTimeout()).contains(Duration.ofMillis(250));
        assertThat(converted.apiNames()).hasSize(1);
        assertThat(converted.credentialsProvider()).isEmpty();
        assertThat(converted.tokenIdentityProvider()).isEmpty();
        assertThat(AwsRequestOverrideConfiguration.from(converted)).isSameAs(converted);
        assertThat(AwsRequestOverrideConfiguration.from(null)).isNull();
    }

    @Test
    void awsRequestSubclassesRoundTripOverrideConfigurationThroughBuilder() {
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("request-access", "request-secret"));

        TestAwsRequest request = TestAwsRequest.builder()
                .overrideConfiguration(o -> o.credentialsProvider(credentialsProvider)
                                             .putHeader("X-Request", "configured"))
                .build();

        assertThat(request.overrideConfiguration()).isPresent();
        assertThat(request.overrideConfiguration().orElseThrow().credentialsProvider()).contains(credentialsProvider);
        assertThat(request.overrideConfiguration().orElseThrow().headers())
                .containsEntry("X-Request", List.of("configured"));

        TestAwsRequest rebuilt = request.toBuilder()
                .overrideConfiguration(request.overrideConfiguration().orElseThrow().toBuilder()
                                              .putRawQueryParameter("q", "v")
                                              .build())
                .build();

        assertThat(rebuilt).isNotEqualTo(request);
        assertThat(rebuilt.overrideConfiguration().orElseThrow().headers())
                .containsEntry("X-Request", List.of("configured"));
        assertThat(rebuilt.overrideConfiguration().orElseThrow().rawQueryParameters())
                .containsEntry("q", List.of("v"));
    }

    @Test
    void responseMetadataExposesRequestIdAndIsImmutable() {
        AwsResponseMetadata metadata = DefaultAwsResponseMetadata.create(
                Map.of(AwsHeader.AWS_REQUEST_ID, "request-123", "extra", "value"));

        assertThat(metadata.requestId()).isEqualTo("request-123");
        assertThat(metadata.toString()).contains("AWS_REQUEST_ID", "extra");
        assertThat(DefaultAwsResponseMetadata.create(Map.of()).requestId()).isEqualTo("UNKNOWN");
        assertThat(metadata).isEqualTo(DefaultAwsResponseMetadata.create(
                Map.of(AwsHeader.AWS_REQUEST_ID, "request-123", "extra", "value")));
        assertThat(metadata).hasSameHashCodeAs(DefaultAwsResponseMetadata.create(
                Map.of(AwsHeader.AWS_REQUEST_ID, "request-123", "extra", "value")));
    }

    @Test
    void awsResponseSubclassesRoundTripMetadataAndHttpResponse() {
        AwsResponseMetadata metadata = DefaultAwsResponseMetadata.create(Map.of(AwsHeader.AWS_REQUEST_ID, "rid-1"));
        SdkHttpResponse httpResponse = SdkHttpResponse.builder()
                .statusCode(201)
                .statusText("Created")
                .putHeader("etag", "abc")
                .build();

        TestAwsResponse response = TestAwsResponse.builder()
                .responseMetadata(metadata)
                .sdkHttpResponse(httpResponse)
                .build();

        assertThat(response.responseMetadata().requestId()).isEqualTo("rid-1");
        assertThat(response.sdkHttpResponse().statusCode()).isEqualTo(201);
        assertThat(response.sdkHttpResponse().statusText()).contains("Created");

        TestAwsResponse copied = response.toBuilder().build();
        assertThat(copied).isEqualTo(response);
        assertThat(copied).hasSameHashCodeAs(response);

        TestAwsResponse changed = response.toBuilder()
                .responseMetadata(DefaultAwsResponseMetadata.create(Map.of(AwsHeader.AWS_REQUEST_ID, "rid-2")))
                .build();
        assertThat(changed).isNotEqualTo(response);
    }

    @Test
    void awsErrorDetailsAndServiceExceptionExposeDiagnostics() {
        SdkHttpResponse httpResponse = SdkHttpResponse.builder()
                .statusCode(429)
                .statusText("Too Many Requests")
                .putHeader("x-amzn-requestid", "request-429")
                .build();
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .serviceName("DynamoDb")
                .errorCode("ThrottlingException")
                .errorMessage("rate exceeded")
                .sdkHttpResponse(httpResponse)
                .rawResponse(SdkBytes.fromUtf8String("{\"message\":\"rate exceeded\"}"))
                .build();

        assertThat(errorDetails.serviceName()).isEqualTo("DynamoDb");
        assertThat(errorDetails.errorCode()).isEqualTo("ThrottlingException");
        assertThat(errorDetails.errorMessage()).isEqualTo("rate exceeded");
        assertThat(errorDetails.rawResponse().asUtf8String()).contains("rate exceeded");
        assertThat(errorDetails.toString()).contains("AwsErrorDetails", "DynamoDb", "ThrottlingException");
        assertThat(errorDetails.toBuilder().build()).isEqualTo(errorDetails);

        AwsServiceException exception = AwsServiceException.builder()
                .awsErrorDetails(errorDetails)
                .requestId("request-429")
                .extendedRequestId("extended-429")
                .statusCode(429)
                .numAttempts(2)
                .build();

        assertThat(exception.awsErrorDetails()).isEqualTo(errorDetails);
        assertThat(exception.isThrottlingException()).isTrue();
        assertThat(exception.getMessage())
                .contains("rate exceeded")
                .contains("Service: DynamoDb")
                .contains("Status Code: 429")
                .contains("Request ID: request-429")
                .contains("Extended Request ID: extended-429");

        AwsServiceException copied = exception.toBuilder().message("explicit message").build();
        assertThat(copied.awsErrorDetails()).isEqualTo(errorDetails);
        assertThat(copied.getMessage()).contains("explicit message");
    }

    @Test
    void serviceExceptionRecognizesAwsClockSkewCodes() {
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .serviceName("S3")
                .errorCode("RequestTimeTooSkewed")
                .errorMessage("time skew")
                .sdkHttpResponse(SdkHttpResponse.builder().statusCode(403).build())
                .build();

        AwsServiceException exception = AwsServiceException.builder()
                .awsErrorDetails(errorDetails)
                .statusCode(403)
                .requestId("clock-skew-request")
                .build();

        assertThat(exception.isClockSkewException()).isTrue();
        assertThat(exception.isThrottlingException()).isFalse();
    }

    @Test
    void retryOnErrorCodeConditionMatchesConfiguredAwsServiceErrorCodesOnly() {
        RetryOnErrorCodeCondition condition = RetryOnErrorCodeCondition.create(
                "RequestTimeout", "ProvisionedThroughputExceededException");

        assertThat(condition.shouldRetry(retryPolicyContextForAwsError("RequestTimeout", 400))).isTrue();
        assertThat(condition.shouldRetry(retryPolicyContextForAwsError("ValidationException", 400))).isFalse();
        assertThat(condition.shouldRetry(RetryPolicyContext.builder()
                .exception(SdkClientException.create("connection failed before an AWS response was received"))
                .build())).isFalse();

        RetryOnErrorCodeCondition setBackedCondition = RetryOnErrorCodeCondition.create(Set.of("InternalError"));
        assertThat(setBackedCondition.shouldRetry(retryPolicyContextForAwsError("InternalError", 500))).isTrue();
    }

    @Test
    void authSchemeObjectsExposeSchemePropertiesAndEquality() {
        SigV4AuthScheme sigV4 = SigV4AuthScheme.builder()
                .signingRegion("us-east-1")
                .signingName("execute-api")
                .disableDoubleEncoding(true)
                .build();
        EndpointAuthScheme endpointAuthScheme = sigV4;

        assertThat(endpointAuthScheme.name()).isEqualTo("sigv4");
        assertThat(endpointAuthScheme.schemeId()).isEqualTo("aws.auth#sigv4");
        assertThat(sigV4.signingRegion()).isEqualTo("us-east-1");
        assertThat(sigV4.signingName()).isEqualTo("execute-api");
        assertThat(sigV4.disableDoubleEncoding()).isTrue();
        assertThat(sigV4.isDisableDoubleEncodingSet()).isTrue();
        assertThat(sigV4).isEqualTo(SigV4AuthScheme.builder()
                .signingRegion("us-east-1")
                .signingName("execute-api")
                .disableDoubleEncoding(true)
                .build());

        SigV4aAuthScheme sigV4a = SigV4aAuthScheme.builder()
                .signingName("s3")
                .addSigningRegion("us-east-1")
                .signingRegionSet(List.of("us-east-1", "us-west-2"))
                .disableDoubleEncoding(false)
                .build();

        assertThat(sigV4a.name()).isEqualTo("sigv4a");
        assertThat(sigV4a.schemeId()).isEqualTo("aws.auth#sigv4a");
        assertThat(sigV4a.signingName()).isEqualTo("s3");
        assertThat(sigV4a.signingRegionSet()).containsExactly("us-east-1", "us-west-2");
        assertThat(sigV4a.disableDoubleEncoding()).isFalse();
        assertThat(sigV4a.isDisableDoubleEncodingSet()).isTrue();
    }

    @Test
    void defaultsModeParsesWireValues() {
        assertThat(DefaultsMode.fromValue("legacy")).isEqualTo(DefaultsMode.LEGACY);
        assertThat(DefaultsMode.fromValue("standard")).isEqualTo(DefaultsMode.STANDARD);
        assertThat(DefaultsMode.fromValue("mobile")).isEqualTo(DefaultsMode.MOBILE);
        assertThat(DefaultsMode.fromValue("cross-region")).isEqualTo(DefaultsMode.CROSS_REGION);
        assertThat(DefaultsMode.fromValue("in-region")).isEqualTo(DefaultsMode.IN_REGION);
        assertThat(DefaultsMode.fromValue("auto")).isEqualTo(DefaultsMode.AUTO);
        assertThat(DefaultsMode.CROSS_REGION.toString()).isEqualTo("cross-region");
        assertThatThrownBy(() -> DefaultsMode.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid defaults mode");
    }

    @Test
    void awsHostNameUtilsParsesCommonAwsEndpointPatterns() {
        assertThat(AwsHostNameUtils.parseSigningRegion("s3.us-west-2.amazonaws.com", "s3"))
                .contains(Region.US_WEST_2);
        assertThat(AwsHostNameUtils.parseSigningRegion("bucket.s3-us-east-2.amazonaws.com", "s3"))
                .contains(Region.US_EAST_2);
        assertThat(AwsHostNameUtils.parseSigningRegion("iam.us-gov.amazonaws.com", "iam"))
                .contains(Region.of("us-gov-west-1"));
        assertThat(AwsHostNameUtils.parseSigningRegion("search-domain.us-west-1.cloudsearch.example.com", "cloudsearch"))
                .contains(Region.US_WEST_1);
        assertThat(AwsHostNameUtils.parseSigningRegion("custom.internal.example", "execute-api")).isEmpty();
        assertThatThrownBy(() -> AwsHostNameUtils.parseSigningRegion(null, "s3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname cannot be null");
    }

    @Test
    void endpointProviderPrefersExplicitOverrideBeforeMetadata() {
        AwsClientEndpointProvider provider = AwsClientEndpointProvider.builder()
                .clientEndpointOverride(URI.create("https://override.example.com/base"))
                .serviceEndpointPrefix("s3")
                .defaultProtocol("https")
                .region(Region.US_WEST_2)
                .build();

        assertThat(provider.clientEndpoint()).isEqualTo(URI.create("https://override.example.com/base"));
        assertThat(provider.isEndpointOverridden()).isTrue();
    }

    @Test
    void endpointProviderUsesServiceSpecificSystemPropertyBeforeServiceMetadata() {
        String property = "aws.endpointUrlIntegrationTestService";
        String previous = System.getProperty(property);
        System.setProperty(property, "https://system-property.example.com");
        try {
            AwsClientEndpointProvider provider = AwsClientEndpointProvider.builder()
                    .serviceEndpointOverrideEnvironmentVariable("AWS_ENDPOINT_URL_INTEGRATION_TEST_SERVICE")
                    .serviceEndpointOverrideSystemProperty(property)
                    .serviceProfileProperty("integration_test_service")
                    .serviceEndpointPrefix("s3")
                    .defaultProtocol("https")
                    .region(Region.US_EAST_1)
                    .build();

            assertThat(provider.clientEndpoint()).isEqualTo(URI.create("https://system-property.example.com"));
            assertThat(provider.isEndpointOverridden()).isTrue();
        } finally {
            restoreProperty(property, previous);
        }
    }

    @Test
    void endpointProviderCanResolveDefaultServiceMetadataEndpoint() {
        AwsClientEndpointProvider provider = AwsClientEndpointProvider.builder()
                .serviceEndpointPrefix("s3")
                .defaultProtocol("https")
                .region(Region.US_WEST_2)
                .dualstackEnabled(false)
                .fipsEnabled(false)
                .build();

        assertThat(provider.clientEndpoint()).isEqualTo(URI.create("https://s3.us-west-2.amazonaws.com"));
        assertThat(provider.isEndpointOverridden()).isFalse();
    }

    @Test
    void endpointFeatureProvidersAndAuthPreferenceResolverReadConfiguredSettings() {
        String dualstackProperty = SdkSystemSetting.AWS_USE_DUALSTACK_ENDPOINT.property();
        String fipsProperty = SdkSystemSetting.AWS_USE_FIPS_ENDPOINT.property();
        String authSchemeProperty = SdkSystemSetting.AWS_AUTH_SCHEME_PREFERENCE.property();
        String previousDualstack = System.getProperty(dualstackProperty);
        String previousFips = System.getProperty(fipsProperty);
        String previousAuthScheme = System.getProperty(authSchemeProperty);
        System.setProperty(dualstackProperty, "true");
        System.setProperty(fipsProperty, "false");
        System.setProperty(authSchemeProperty, "");
        try {
            ProfileFile profileFile = ProfileFile.builder()
                    .type(ProfileFile.Type.CONFIGURATION)
                    .content("""
                            [profile native-test]
                            use_dualstack_endpoint = true
                            use_fips_endpoint = false
                            auth_scheme_preference = sigv4a, sigv4
                            """)
                    .build();

            assertThat(DualstackEnabledProvider.builder()
                    .profileFile(() -> profileFile)
                    .profileName("native-test")
                    .build()
                    .isDualstackEnabled()).contains(true);
            assertThat(FipsEnabledProvider.builder()
                    .profileFile(() -> profileFile)
                    .profileName("native-test")
                    .build()
                    .isFipsEnabled()).contains(false);
            assertThat(AuthSchemePreferenceResolver.builder()
                    .profileFile(() -> profileFile)
                    .profileName("native-test")
                    .build()
                    .resolveAuthSchemePreference()).containsExactly("sigv4a", "sigv4");
        } finally {
            restoreProperty(dualstackProperty, previousDualstack);
            restoreProperty(fipsProperty, previousFips);
            restoreProperty(authSchemeProperty, previousAuthScheme);
        }
    }

    @Test
    void accountIdEndpointModeResolverReadsSystemProfileAndDefaultModes() {
        String property = SdkSystemSetting.AWS_ACCOUNT_ID_ENDPOINT_MODE.property();
        String previous = System.getProperty(property);
        ProfileFile profileFile = ProfileFile.builder()
                .type(ProfileFile.Type.CONFIGURATION)
                .content("""
                        [profile native-test]
                        account_id_endpoint_mode = disabled

                        [profile without-account-id-mode]
                        region = us-west-2
                        """)
                .build();

        System.setProperty(property, "required");
        try {
            assertThat(AccountIdEndpointModeResolver.create()
                    .profileFile(() -> profileFile)
                    .profileName("native-test")
                    .defaultMode(AccountIdEndpointMode.PREFERRED)
                    .resolve()).isEqualTo(AccountIdEndpointMode.REQUIRED);

            System.clearProperty(property);

            assertThat(AccountIdEndpointModeResolver.create()
                    .profileFile(() -> profileFile)
                    .profileName("native-test")
                    .defaultMode(AccountIdEndpointMode.PREFERRED)
                    .resolve()).isEqualTo(AccountIdEndpointMode.DISABLED);
            assertThat(AccountIdEndpointModeResolver.create()
                    .profileFile(() -> profileFile)
                    .profileName("without-account-id-mode")
                    .defaultMode(AccountIdEndpointMode.REQUIRED)
                    .resolve()).isEqualTo(AccountIdEndpointMode.REQUIRED);
        } finally {
            restoreProperty(property, previous);
        }
    }

    @Test
    void authPreferenceResolverUsesSystemPropertyBeforeProfile() {
        String property = SdkSystemSetting.AWS_AUTH_SCHEME_PREFERENCE.property();
        String previous = System.getProperty(property);
        System.setProperty(property, " sigv4a, bearer ");
        try {
            ProfileFile profileFile = ProfileFile.builder()
                    .type(ProfileFile.Type.CONFIGURATION)
                    .content("""
                            [profile native-test]
                            auth_scheme_preference = sigv4
                            """)
                    .build();

            assertThat(AuthSchemePreferenceResolver.builder()
                    .profileFile(() -> profileFile)
                    .profileName("native-test")
                    .build()
                    .resolveAuthSchemePreference()).containsExactly("sigv4a", "bearer");
        } finally {
            restoreProperty(property, previous);
        }
    }

    @Test
    void presignRequestRequiresAndCopiesSignatureDuration() {
        TestPresignRequest request = TestPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .build();

        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(request).isEqualTo(TestPresignRequest.builder().signatureDuration(Duration.ofMinutes(15)).build());
        assertThat(request).hasSameHashCodeAs(TestPresignRequest.builder().signatureDuration(Duration.ofMinutes(15)).build());
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThatThrownBy(() -> TestPresignRequest.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signatureDuration");
    }

    @Test
    void presignedRequestBuildsUrlSignedHeadersPayloadAndHttpRequest() {
        SdkHttpFullRequest httpRequest = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.PUT)
                .uri(URI.create("https://example-bucket.s3.us-west-2.amazonaws.com/object.txt?X-Amz-Algorithm=AWS4"))
                .putHeader("host", "example-bucket.s3.us-west-2.amazonaws.com")
                .build();
        Instant expiration = Instant.parse("2025-01-01T00:00:00Z");
        SdkBytes payload = SdkBytes.fromUtf8String("payload");

        TestPresignedRequest request = TestPresignedRequest.builder()
                .expiration(expiration)
                .isBrowserExecutable(false)
                .signedHeaders(Map.of("host", List.of("example-bucket.s3.us-west-2.amazonaws.com")))
                .signedPayload(payload)
                .httpRequest(httpRequest)
                .build();

        assertThat(request.url().toString())
                .isEqualTo("https://example-bucket.s3.us-west-2.amazonaws.com/object.txt?X-Amz-Algorithm=AWS4");
        assertThat(request.expiration()).isEqualTo(expiration);
        assertThat(request.isBrowserExecutable()).isFalse();
        assertThat(request.signedHeaders()).containsEntry(
                "host", List.of("example-bucket.s3.us-west-2.amazonaws.com"));
        assertThat(request.signedPayload()).contains(payload);
        assertThat(request.httpRequest()).isEqualTo(httpRequest);
        assertThat(request.toBuilder().build()).isEqualTo(request);
        assertThat(request).hasSameHashCodeAs(request.toBuilder().build());
    }

    @Test
    void presignedRequestValidatesRequiredFields() {
        assertThatThrownBy(() -> TestPresignedRequest.builder()
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", List.of("example.com")))
                .httpRequest(SdkHttpFullRequest.builder()
                        .method(SdkHttpMethod.GET)
                        .uri(URI.create("https://example.com"))
                        .build())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiration");

        assertThatThrownBy(() -> TestPresignedRequest.builder()
                .expiration(Instant.parse("2025-01-01T00:00:00Z"))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of())
                .httpRequest(SdkHttpFullRequest.builder()
                        .method(SdkHttpMethod.GET)
                        .uri(URI.create("https://example.com"))
                        .build())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signedHeaders");
    }

    private static RetryPolicyContext retryPolicyContextForAwsError(String errorCode, int statusCode) {
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .serviceName("TestService")
                .errorCode(errorCode)
                .errorMessage("service error")
                .sdkHttpResponse(SdkHttpResponse.builder().statusCode(statusCode).build())
                .build();
        AwsServiceException exception = AwsServiceException.builder()
                .awsErrorDetails(errorDetails)
                .statusCode(statusCode)
                .requestId("retry-condition-request")
                .build();

        return RetryPolicyContext.builder()
                .exception(exception)
                .httpStatusCode(statusCode)
                .retriesAttempted(1)
                .build();
    }

    private static void restoreProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    private static final class StaticTokenProvider implements IdentityProvider<TokenIdentity> {
        private final TokenIdentity tokenIdentity;

        private StaticTokenProvider(String token) {
            this.tokenIdentity = TokenIdentity.create(token);
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }

        @Override
        public CompletableFuture<? extends TokenIdentity> resolveIdentity(ResolveIdentityRequest request) {
            return CompletableFuture.completedFuture(tokenIdentity);
        }
    }

    private static final class TestAwsRequest extends AwsRequest {
        private TestAwsRequest(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        public Builder toBuilder() {
            return new Builder(this);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return List.of();
        }

        private static final class Builder extends AwsRequest.BuilderImpl {
            private Builder() {
            }

            private Builder(TestAwsRequest request) {
                super(request);
            }

            @Override
            public Builder overrideConfiguration(AwsRequestOverrideConfiguration awsRequestOverrideConfig) {
                super.overrideConfiguration(awsRequestOverrideConfig);
                return this;
            }

            @Override
            public Builder overrideConfiguration(Consumer<AwsRequestOverrideConfiguration.Builder> builderConsumer) {
                super.overrideConfiguration(builderConsumer);
                return this;
            }

            @Override
            public TestAwsRequest build() {
                return new TestAwsRequest(this);
            }
        }
    }

    private static final class TestAwsResponse extends AwsResponse {
        private TestAwsResponse(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        public Builder toBuilder() {
            return new Builder(this);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return List.of();
        }

        private static final class Builder extends AwsResponse.BuilderImpl {
            private Builder() {
            }

            private Builder(TestAwsResponse response) {
                super(response);
            }

            @Override
            public Builder responseMetadata(AwsResponseMetadata responseMetadata) {
                super.responseMetadata(responseMetadata);
                return this;
            }

            @Override
            public Builder sdkHttpResponse(SdkHttpResponse sdkHttpResponse) {
                super.sdkHttpResponse(sdkHttpResponse);
                return this;
            }

            @Override
            public TestAwsResponse build() {
                return new TestAwsResponse(this);
            }
        }
    }

    private static final class TestPresignRequest extends PresignRequest {
        private TestPresignRequest(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        private Builder toBuilder() {
            return new Builder(this);
        }

        private static final class Builder extends PresignRequest.DefaultBuilder<Builder> {
            private Builder() {
            }

            private Builder(TestPresignRequest request) {
                super(request);
            }

            @Override
            public TestPresignRequest build() {
                return new TestPresignRequest(this);
            }
        }
    }

    private static final class TestPresignedRequest extends PresignedRequest {
        private TestPresignedRequest(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        private Builder toBuilder() {
            return new Builder(this);
        }

        private static final class Builder extends PresignedRequest.DefaultBuilder<Builder> {
            private Builder() {
            }

            private Builder(TestPresignedRequest request) {
                super(request);
            }

            @Override
            public TestPresignedRequest build() {
                return new TestPresignedRequest(this);
            }
        }
    }
}
