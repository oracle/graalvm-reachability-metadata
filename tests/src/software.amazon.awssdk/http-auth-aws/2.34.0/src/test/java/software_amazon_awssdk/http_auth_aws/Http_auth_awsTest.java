/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.http_auth_aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.checksums.DefaultChecksumAlgorithm;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.scheme.AwsV4AuthScheme;
import software.amazon.awssdk.http.auth.aws.scheme.AwsV4aAuthScheme;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4aHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.RegionSet;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignedRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.identity.spi.IdentityProviders;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;

public class Http_auth_awsTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2020-01-02T03:04:05Z"), ZoneOffset.UTC);
    private static final AwsCredentialsIdentity BASIC_CREDENTIALS =
        AwsCredentialsIdentity.create("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
    private static final AwsSessionCredentialsIdentity SESSION_CREDENTIALS =
        AwsSessionCredentialsIdentity.create(
            "AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "session-token");

    @Test
    void regionSetParsesTrimsComparesAndProtectsValues() {
        RegionSet globalRegionSet = RegionSet.GLOBAL;
        RegionSet parsedRegionSet = RegionSet.create(" us-east-1, eu-west-1 ");
        RegionSet collectionRegionSet = RegionSet.create(Arrays.asList("eu-west-1", "us-east-1"));

        assertThat(globalRegionSet.asString()).isEqualTo("*");
        assertThat(globalRegionSet.asSet()).containsExactly("*");
        assertThat(parsedRegionSet.asSet()).containsExactlyInAnyOrder("us-east-1", "eu-west-1");
        assertThat(parsedRegionSet).isEqualTo(collectionRegionSet);
        assertThat(parsedRegionSet.hashCode()).isEqualTo(collectionRegionSet.hashCode());
        assertThatThrownBy(() -> parsedRegionSet.asSet().add("ap-south-1"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RegionSet.create("  "));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> RegionSet.create(Arrays.asList("us-east-1", "")));
    }

    @Test
    void authSchemesExposeSchemeIdsIdentityProvidersAndSigner() throws Exception {
        StaticCredentialsIdentityProvider provider = new StaticCredentialsIdentityProvider(BASIC_CREDENTIALS);
        IdentityProviders providers = IdentityProviders.builder().putIdentityProvider(provider).build();

        AwsV4AuthScheme v4Scheme = AwsV4AuthScheme.create();
        AwsV4aAuthScheme v4aScheme = AwsV4aAuthScheme.create();

        assertThat(v4Scheme.schemeId()).isEqualTo(AwsV4AuthScheme.SCHEME_ID);
        assertThat(v4Scheme.identityProvider(providers)).isSameAs(provider);
        assertThat(v4Scheme.signer()).isNotNull();
        assertThat(v4Scheme.signer()).isSameAs(AwsV4AuthScheme.create().signer());
        assertThat(v4Scheme.identityProvider(providers).resolveIdentity().get(5, TimeUnit.SECONDS))
            .isSameAs(BASIC_CREDENTIALS);

        assertThat(v4aScheme.schemeId()).isEqualTo(AwsV4aAuthScheme.SCHEME_ID);
        assertThat(v4aScheme.identityProvider(providers)).isSameAs(provider);
    }

    @Test
    void v4SignerAddsDeterministicAuthorizationAndPayloadHeaders() throws IOException {
        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        SdkHttpFullRequest unsignedRequest = baseRequest()
            .method(SdkHttpMethod.POST)
            .putHeader("Content-Type", "application/json")
            .build();
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("{\"message\":\"hello\"}");

        SignedRequest signedRequest = signer.sign(request -> request
            .identity(SESSION_CREDENTIALS)
            .request(unsignedRequest)
            .payload(payload)
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "execute-api")
            .putProperty(AwsV4HttpSigner.REGION_NAME, "us-east-1")
            .putProperty(AwsV4HttpSigner.SIGNING_CLOCK, FIXED_CLOCK)
            .putProperty(AwsV4HttpSigner.DOUBLE_URL_ENCODE, false)
            .putProperty(AwsV4HttpSigner.NORMALIZE_PATH, false));

        SdkHttpRequest request = signedRequest.request();
        String authorizationHeader = requiredHeader(request, "Authorization");

        assertThat(request.method()).isEqualTo(SdkHttpMethod.POST);
        assertThat(requiredHeader(request, "X-Amz-Date")).isEqualTo("20200102T030405Z");
        assertThat(requiredHeader(request, "X-Amz-Security-Token")).isEqualTo("session-token");
        assertThat(requiredHeader(request, "X-Amz-Content-Sha256"))
            .isEqualTo("9b2d43affbf49a367028df2e1414f84c0e099ac98c3d54a8a80157fd7771af25");
        assertThat(authorizationHeader).startsWith("AWS4-HMAC-SHA256 ");
        assertThat(authorizationHeader).contains("Credential=AKIDEXAMPLE/20200102/us-east-1/execute-api/aws4_request");
        assertThat(authorizationHeader).contains("SignedHeaders=");
        assertThat(authorizationHeader).contains("Signature=");
        assertThat(new String(signedRequest.payload().orElseThrow().newStream().readAllBytes(), StandardCharsets.UTF_8))
            .isEqualTo("{\"message\":\"hello\"}");
    }

    @Test
    void v4SignerCanPresignRequestsIntoQueryParameters() {
        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        SdkHttpFullRequest unsignedRequest = baseRequest()
            .method(SdkHttpMethod.GET)
            .appendRawQueryParameter("already", "present")
            .build();

        SignedRequest signedRequest = signer.sign(request -> request
            .identity(BASIC_CREDENTIALS)
            .request(unsignedRequest)
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "execute-api")
            .putProperty(AwsV4HttpSigner.REGION_NAME, "us-east-1")
            .putProperty(AwsV4HttpSigner.SIGNING_CLOCK, FIXED_CLOCK)
            .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.QUERY_STRING)
            .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, Duration.ofMinutes(10)));

        SdkHttpRequest request = signedRequest.request();

        assertThat(request.firstMatchingHeader("Authorization")).isEmpty();
        assertThat(queryParameter(request, "already")).contains("present");
        assertThat(queryParameter(request, "X-Amz-Algorithm")).contains("AWS4-HMAC-SHA256");
        assertThat(queryParameter(request, "X-Amz-Date")).contains("20200102T030405Z");
        assertThat(queryParameter(request, "X-Amz-Expires")).contains("600");
        assertThat(queryParameter(request, "X-Amz-Credential"))
            .contains("AKIDEXAMPLE/20200102/us-east-1/execute-api/aws4_request");
        assertThat(queryParameter(request, "X-Amz-SignedHeaders")).contains("host");
        assertThat(queryParameter(request, "X-Amz-Signature"))
            .isPresent()
            .hasValueSatisfying(signature -> assertThat(signature).hasSize(64));
        assertThat(signedRequest.payload()).isEmpty();
    }

    @Test
    void v4SignerSupportsFlexibleChecksumsAndChunkEncoding() throws IOException {
        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        SdkHttpFullRequest unsignedRequest = baseRequest()
            .method(SdkHttpMethod.PUT)
            .putHeader("Content-Length", "11")
            .build();

        SignedRequest signedRequest = signer.sign(request -> request
            .identity(BASIC_CREDENTIALS)
            .request(unsignedRequest)
            .payload(ContentStreamProvider.fromUtf8String("hello world"))
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "s3")
            .putProperty(AwsV4HttpSigner.REGION_NAME, "us-east-1")
            .putProperty(AwsV4HttpSigner.SIGNING_CLOCK, FIXED_CLOCK)
            .putProperty(AwsV4HttpSigner.CHUNK_ENCODING_ENABLED, true)
            .putProperty(AwsV4HttpSigner.CHECKSUM_ALGORITHM, DefaultChecksumAlgorithm.CRC32));

        SdkHttpRequest request = signedRequest.request();
        String signedPayload = new String(
            signedRequest.payload().orElseThrow().newStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(requiredHeader(request, "Content-Encoding")).contains("aws-chunked");
        assertThat(requiredHeader(request, "X-Amz-Trailer")).isEqualTo("x-amz-checksum-crc32");
        assertThat(requiredHeader(request, "X-Amz-Decoded-Content-Length")).isEqualTo("11");
        assertThat(requiredHeader(request, "X-Amz-Content-Sha256"))
            .isEqualTo("STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER");
        assertThat(signedPayload).contains(";chunk-signature=");
        assertThat(signedPayload).contains("hello world");
        assertThat(signedPayload).contains("x-amz-checksum-crc32");
    }

    @Test
    void v4SignerSignsAsyncRequests() throws Exception {
        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        SdkHttpFullRequest unsignedRequest = baseRequest().method(SdkHttpMethod.HEAD).build();

        CompletableFuture<AsyncSignedRequest> signedRequestFuture = signer.signAsync(request -> request
            .identity(BASIC_CREDENTIALS)
            .request(unsignedRequest)
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "execute-api")
            .putProperty(AwsV4HttpSigner.REGION_NAME, "us-east-1")
            .putProperty(AwsV4HttpSigner.SIGNING_CLOCK, FIXED_CLOCK));

        AsyncSignedRequest signedRequest = signedRequestFuture.get(5, TimeUnit.SECONDS);

        assertThat(requiredHeader(signedRequest.request(), "Authorization"))
            .contains("Credential=AKIDEXAMPLE/20200102/us-east-1/execute-api/aws4_request");
        assertThat(requiredHeader(signedRequest.request(), "X-Amz-Date")).isEqualTo("20200102T030405Z");
        assertThat(signedRequest.payload()).isEmpty();
    }

    @Test
    void v4SignerRejectsInvalidPresignConfiguration() {
        AwsV4HttpSigner signer = AwsV4HttpSigner.create();
        SdkHttpFullRequest unsignedRequest = baseRequest().method(SdkHttpMethod.GET).build();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> signer.sign(request -> request
            .identity(BASIC_CREDENTIALS)
            .request(unsignedRequest)
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "execute-api")
            .putProperty(AwsV4HttpSigner.REGION_NAME, "us-east-1")
            .putProperty(AwsV4HttpSigner.SIGNING_CLOCK, FIXED_CLOCK)
            .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.HEADER)
            .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, Duration.ofMinutes(10))))
            .withMessageContaining("ExpirationDuration")
            .withMessageContaining("HEADER");

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> signer.sign(request -> request
            .identity(BASIC_CREDENTIALS)
            .request(unsignedRequest)
            .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "execute-api")
            .putProperty(AwsV4HttpSigner.REGION_NAME, "us-east-1")
            .putProperty(AwsV4HttpSigner.SIGNING_CLOCK, FIXED_CLOCK)
            .putProperty(AwsV4HttpSigner.AUTH_LOCATION, AwsV4FamilyHttpSigner.AuthLocation.QUERY_STRING)
            .putProperty(AwsV4HttpSigner.EXPIRATION_DURATION, Duration.ofDays(8))))
            .withMessageContaining("at most 7 days");
    }

    @Test
    void v4aSignerReportsMissingOptionalCrtDependency() {
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(AwsV4aHttpSigner::create)
            .withMessageContaining("software.amazon.awssdk:http-auth-aws-crt")
            .withMessageContaining("CRT-V4a signing");

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> AwsV4aAuthScheme.create().signer())
            .withMessageContaining("software.amazon.awssdk:http-auth-aws-crt")
            .withMessageContaining("CRT-V4a signing");
    }

    private static SdkHttpFullRequest.Builder baseRequest() {
        return SdkHttpFullRequest.builder()
            .uri(URI.create("https://example.amazonaws.com/prod/resource"));
    }

    private static String requiredHeader(SdkHttpRequest request, String name) {
        return request.firstMatchingHeader(name).orElseThrow(() -> new AssertionError("Missing header " + name));
    }

    private static Optional<String> queryParameter(SdkHttpRequest request, String name) {
        return request.firstMatchingRawQueryParameter(name);
    }

    private static final class StaticCredentialsIdentityProvider implements IdentityProvider<AwsCredentialsIdentity> {
        private final AwsCredentialsIdentity credentials;

        private StaticCredentialsIdentityProvider(AwsCredentialsIdentity credentials) {
            this.credentials = credentials;
        }

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(ResolveIdentityRequest request) {
            return CompletableFuture.completedFuture(credentials);
        }
    }
}
