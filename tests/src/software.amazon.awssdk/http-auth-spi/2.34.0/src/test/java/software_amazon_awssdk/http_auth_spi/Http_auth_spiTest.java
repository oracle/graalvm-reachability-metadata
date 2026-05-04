/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.http_auth_spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.spi.scheme.AuthScheme;
import software.amazon.awssdk.http.auth.spi.scheme.AuthSchemeOption;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignRequest;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignedRequest;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignerProperty;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.Identity;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.identity.spi.IdentityProviders;
import software.amazon.awssdk.identity.spi.IdentityProperty;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;
import software.amazon.awssdk.identity.spi.TokenIdentity;

public class Http_auth_spiTest {
    private static final SignerProperty<String> SIGNER_NAME = SignerProperty.create(
            Http_auth_spiTest.class, "SignerName");
    private static final SignerProperty<String> MISSING_SIGNER_NAME = SignerProperty.create(
            Http_auth_spiTest.class, "MissingSignerName");
    private static final IdentityProperty<String> REGION = IdentityProperty.create(Http_auth_spiTest.class, "Region");
    private static final IdentityProperty<Integer> PRIORITY = IdentityProperty.create(
            Http_auth_spiTest.class, "Priority");

    @Test
    void signerPropertiesCompareByDeclaringTypeAndName() {
        SignerProperty<String> first = SignerProperty.create(Http_auth_spiTest.class, "Audience");
        SignerProperty<String> differentName = SignerProperty.create(Http_auth_spiTest.class, "Scope");
        SignerProperty<String> differentOwner = SignerProperty.create(RecordingSigner.class, "Audience");

        assertThat(first).isNotEqualTo(differentName).isNotEqualTo(differentOwner);
        assertThat(first.toString()).contains("Audience");
        assertThat(HttpSigner.SIGNING_CLOCK.toString()).contains("SigningClock");
        assertThatThrownBy(() -> SignerProperty.create(Http_auth_spiTest.class, "Audience"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No duplicate SignerProperty names allowed");
    }

    @Test
    void authSchemeOptionRetainsPropertiesSupportsIterationAndCopying() {
        AuthSchemeOption option = AuthSchemeOption.builder()
                .schemeId("aws.auth#sigv4")
                .putIdentityProperty(REGION, "us-east-1")
                .putIdentityPropertyIfAbsent(REGION, "eu-west-1")
                .putIdentityProperty(PRIORITY, 10)
                .putSignerProperty(SIGNER_NAME, "primary")
                .putSignerPropertyIfAbsent(SIGNER_NAME, "secondary")
                .build();

        assertThat(option.schemeId()).isEqualTo("aws.auth#sigv4");
        assertThat(option.identityProperty(REGION)).isEqualTo("us-east-1");
        assertThat(option.identityProperty(PRIORITY)).isEqualTo(10);
        assertThat(option.signerProperty(SIGNER_NAME)).isEqualTo("primary");
        assertThat(option.signerProperty(MISSING_SIGNER_NAME)).isNull();

        Map<IdentityProperty<?>, Object> identityProperties = new LinkedHashMap<>();
        option.forEachIdentityProperty(new AuthSchemeOption.IdentityPropertyConsumer() {
            @Override
            public <T> void accept(IdentityProperty<T> property, T value) {
                identityProperties.put(property, value);
            }
        });
        assertThat(identityProperties).containsEntry(REGION, "us-east-1").containsEntry(PRIORITY, 10);

        Map<SignerProperty<?>, Object> signerProperties = new LinkedHashMap<>();
        option.forEachSignerProperty(new AuthSchemeOption.SignerPropertyConsumer() {
            @Override
            public <T> void accept(SignerProperty<T> property, T value) {
                signerProperties.put(property, value);
            }
        });
        assertThat(signerProperties).containsEntry(SIGNER_NAME, "primary");

        AuthSchemeOption copied = option.toBuilder()
                .schemeId("aws.auth#sigv4a")
                .putIdentityProperty(REGION, "us-west-2")
                .putSignerProperty(SIGNER_NAME, "copied")
                .build();

        assertThat(copied.schemeId()).isEqualTo("aws.auth#sigv4a");
        assertThat(copied.identityProperty(REGION)).isEqualTo("us-west-2");
        assertThat(copied.signerProperty(SIGNER_NAME)).isEqualTo("copied");
        assertThat(option.schemeId()).isEqualTo("aws.auth#sigv4");
        assertThat(option.identityProperty(REGION)).isEqualTo("us-east-1");
        assertThat(option.signerProperty(SIGNER_NAME)).isEqualTo("primary");
    }

    @Test
    void buildersRejectMissingRequiredValues() {
        AwsCredentialsIdentity identity = AwsCredentialsIdentity.create("access-key", "secret-key");
        SdkHttpFullRequest request = httpRequest("/validation");

        assertThatThrownBy(() -> AuthSchemeOption.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("schemeId");
        assertThatThrownBy(() -> AuthSchemeOption.builder().schemeId(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemeId");

        assertThatThrownBy(() -> SignRequest.builder(identity).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> SignRequest.builder((AwsCredentialsIdentity) null).request(request).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("identity");

        assertThatThrownBy(() -> AsyncSignRequest.builder(identity).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> AsyncSignRequest.builder((AwsCredentialsIdentity) null).request(request).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("identity");

        assertThatThrownBy(() -> SignedRequest.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> AsyncSignedRequest.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("request");
    }

    @Test
    void signRequestBuilderRetainsIdentityRequestPayloadAndProperties() {
        AwsCredentialsIdentity identity = AwsCredentialsIdentity.create("access-key", "secret-key");
        SdkHttpFullRequest request = httpRequest("/sync");
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("hello");
        Clock signingClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

        SignRequest<AwsCredentialsIdentity> signRequest = SignRequest.builder(identity)
                .request(request)
                .payload(payload)
                .putProperty(HttpSigner.SIGNING_CLOCK, signingClock)
                .putProperty(SIGNER_NAME, "sigv4")
                .build();

        assertThat(signRequest.identity()).isSameAs(identity);
        assertThat(signRequest.request()).isSameAs(request);
        assertThat(signRequest.payload()).containsSame(payload);
        assertThat(signRequest.property(HttpSigner.SIGNING_CLOCK)).isSameAs(signingClock);
        assertThat(signRequest.property(SIGNER_NAME)).isEqualTo("sigv4");
        assertThat(signRequest.hasProperty(SIGNER_NAME)).isTrue();
        assertThat(signRequest.hasProperty(MISSING_SIGNER_NAME)).isFalse();
        assertThat(signRequest.requireProperty(SIGNER_NAME)).isEqualTo("sigv4");
        assertThat(signRequest.requireProperty(MISSING_SIGNER_NAME, "default")).isEqualTo("default");
        assertThatThrownBy(() -> signRequest.requireProperty(MISSING_SIGNER_NAME))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("MissingSignerName");
        assertThat(readUtf8(signRequest.payload())).isEqualTo("hello");

        SignRequest<AwsCredentialsIdentity> copied = signRequest.toBuilder()
                .putProperty(SIGNER_NAME, "sigv4a")
                .build();
        assertThat(copied.property(SIGNER_NAME)).isEqualTo("sigv4a");
        assertThat(signRequest.property(SIGNER_NAME)).isEqualTo("sigv4");
    }

    @Test
    void copyConsumerMutatesSignRequestWhilePreservingOriginal() {
        AwsCredentialsIdentity identity = AwsCredentialsIdentity.create("access-key", "secret-key");
        SdkHttpFullRequest originalRequest = httpRequest("/copy-source");
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("copy-body");
        SignRequest<AwsCredentialsIdentity> signRequest = SignRequest.builder(identity)
                .request(originalRequest)
                .payload(payload)
                .putProperty(SIGNER_NAME, "original")
                .build();
        SdkHttpFullRequest copiedRequest = originalRequest.toBuilder()
                .encodedPath("/copy-target")
                .putHeader("X-Copy", "applied")
                .build();

        SignRequest<AwsCredentialsIdentity> copied = signRequest.copy(builder -> builder
                .request(copiedRequest)
                .putProperty(SIGNER_NAME, "copied"));

        assertThat(copied.identity()).isSameAs(identity);
        assertThat(copied.request()).isSameAs(copiedRequest);
        assertThat(copied.payload()).containsSame(payload);
        assertThat(copied.property(SIGNER_NAME)).isEqualTo("copied");
        assertThat(copied.request().firstMatchingHeader("X-Copy")).contains("applied");

        assertThat(signRequest.request()).isSameAs(originalRequest);
        assertThat(signRequest.payload()).containsSame(payload);
        assertThat(signRequest.property(SIGNER_NAME)).isEqualTo("original");
        assertThat(signRequest.request().firstMatchingHeader("X-Copy")).isEmpty();
    }

    @Test
    void signedRequestBuilderSupportsOptionalPayloadAndCopying() {
        SdkHttpFullRequest originalRequest = httpRequest("/unsigned");
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("body");

        SignedRequest signedRequest = SignedRequest.builder()
                .request(originalRequest)
                .payload(payload)
                .build();

        assertThat(signedRequest.request()).isSameAs(originalRequest);
        assertThat(signedRequest.payload()).containsSame(payload);
        assertThat(readUtf8(signedRequest.payload())).isEqualTo("body");

        SdkHttpFullRequest authorizedRequest = originalRequest.toBuilder()
                .putHeader("Authorization", "test-signature")
                .build();
        SignedRequest copied = signedRequest.toBuilder().request(authorizedRequest).build();
        assertThat(copied.request().firstMatchingHeader("Authorization")).contains("test-signature");
        assertThat(signedRequest.request().firstMatchingHeader("Authorization")).isEmpty();

        SignedRequest withoutPayload = SignedRequest.builder().request(originalRequest).build();
        assertThat(withoutPayload.payload()).isEmpty();
    }

    @Test
    void asyncSignAndSignedRequestsRetainReactivePayloadsAndProperties() {
        TokenIdentity identity = TokenIdentity.create("token");
        SdkHttpFullRequest request = httpRequest("/async");
        FixedPublisher requestPayload = new FixedPublisher("request-body");
        FixedPublisher responsePayload = new FixedPublisher("signed-body");

        AsyncSignRequest<TokenIdentity> asyncSignRequest = AsyncSignRequest.builder(identity)
                .request(request)
                .payload(requestPayload)
                .putProperty(SIGNER_NAME, "async-signer")
                .build();

        assertThat(asyncSignRequest.identity()).isSameAs(identity);
        assertThat(asyncSignRequest.request()).isSameAs(request);
        assertThat(asyncSignRequest.payload()).containsSame(requestPayload);
        assertThat(asyncSignRequest.property(SIGNER_NAME)).isEqualTo("async-signer");
        assertThat(asyncSignRequest.toBuilder().putProperty(SIGNER_NAME, "copied").build().property(SIGNER_NAME))
                .isEqualTo("copied");

        AsyncSignedRequest asyncSignedRequest = AsyncSignedRequest.builder()
                .request(request)
                .payload(responsePayload)
                .build();

        assertThat(asyncSignedRequest.request()).isSameAs(request);
        assertThat(asyncSignedRequest.payload()).containsSame(responsePayload);
        assertThat(asyncSignedRequest.toBuilder().request(httpRequest("/async-copy")).build().request().encodedPath())
                .isEqualTo("/async-copy");
        assertThat(AsyncSignedRequest.builder().request(request).build().payload()).isEmpty();
    }

    @Test
    void noOpSignerReturnsRequestAndPayloadWithoutModification() throws Exception {
        RecordingSigner<AwsCredentialsIdentity> factory = new RecordingSigner<>();
        HttpSigner<AwsCredentialsIdentity> noOpSigner = factory.doNotSign();
        AwsCredentialsIdentity identity = AwsCredentialsIdentity.create("access-key", "secret-key");
        SdkHttpFullRequest request = httpRequest("/no-op");
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("unchanged");

        SignedRequest signedRequest = noOpSigner.sign(SignRequest.builder(identity)
                .request(request)
                .payload(payload)
                .build());

        assertThat(signedRequest.request()).isSameAs(request);
        assertThat(signedRequest.payload()).containsSame(payload);
        assertThat(readUtf8(signedRequest.payload())).isEqualTo("unchanged");

        FixedPublisher asyncPayload = new FixedPublisher("async-unchanged");
        AsyncSignedRequest asyncSignedRequest = noOpSigner.signAsync(AsyncSignRequest.builder(identity)
                        .request(request)
                        .payload(asyncPayload)
                        .build())
                .get(5, TimeUnit.SECONDS);

        assertThat(asyncSignedRequest.request()).isSameAs(request);
        assertThat(asyncSignedRequest.payload()).containsSame(asyncPayload);
    }

    @Test
    void consumerSigningMethodsBuildRequestsBeforeDelegating() throws Exception {
        RecordingSigner<TokenIdentity> signer = new RecordingSigner<>();
        TokenIdentity identity = TokenIdentity.create("consumer-token");
        SdkHttpFullRequest request = httpRequest("/consumer");
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("consumer-body");

        SignedRequest signedRequest = signer.sign(builder -> builder
                .identity(identity)
                .request(request)
                .payload(payload)
                .putProperty(SIGNER_NAME, "consumer-sync"));

        assertThat(signedRequest.request().firstMatchingHeader("X-Recorded-Signer")).contains("sync");
        assertThat(signer.lastSignRequest.identity()).isSameAs(identity);
        assertThat(signer.lastSignRequest.payload()).containsSame(payload);
        assertThat(signer.lastSignRequest.property(SIGNER_NAME)).isEqualTo("consumer-sync");

        FixedPublisher asyncPayload = new FixedPublisher("consumer-async-body");
        AsyncSignedRequest asyncSignedRequest = signer.signAsync(builder -> builder
                        .identity(identity)
                        .request(request)
                        .payload(asyncPayload)
                        .putProperty(SIGNER_NAME, "consumer-async"))
                .get(5, TimeUnit.SECONDS);

        assertThat(asyncSignedRequest.request().firstMatchingHeader("X-Recorded-Signer")).contains("async");
        assertThat(signer.lastAsyncSignRequest.identity()).isSameAs(identity);
        assertThat(signer.lastAsyncSignRequest.payload()).containsSame(asyncPayload);
        assertThat(signer.lastAsyncSignRequest.property(SIGNER_NAME)).isEqualTo("consumer-async");
    }

    @Test
    void authSchemeSelectsIdentityProviderAndSignerFromPublicInterfaces() throws Exception {
        TokenIdentity identity = TokenIdentity.create("scheme-token");
        TokenIdentityProvider provider = new TokenIdentityProvider(identity);
        RecordingSigner<TokenIdentity> signer = new RecordingSigner<>();
        AuthScheme<TokenIdentity> authScheme = new StaticAuthScheme("aws.auth#bearer", provider, signer);
        IdentityProviders identityProviders = IdentityProviders.builder().putIdentityProvider(provider).build();

        assertThat(authScheme.schemeId()).isEqualTo("aws.auth#bearer");
        assertThat(authScheme.identityProvider(identityProviders)).isSameAs(provider);
        TokenIdentity resolvedIdentity = authScheme.identityProvider(identityProviders)
                .resolveIdentity()
                .get(5, TimeUnit.SECONDS);
        assertThat(resolvedIdentity).isSameAs(identity);

        SdkHttpFullRequest request = httpRequest("/scheme");
        SignedRequest signedRequest = authScheme.signer().sign(SignRequest.builder(identity).request(request).build());
        assertThat(signedRequest.request().firstMatchingHeader("X-Recorded-Signer")).contains("sync");
    }

    private static SdkHttpFullRequest httpRequest(String path) {
        return SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .protocol("https")
                .host("example.com")
                .encodedPath(path)
                .putHeader("Host", "example.com")
                .appendRawQueryParameter("mode", "test")
                .build();
    }

    private static String readUtf8(Optional<ContentStreamProvider> payload) {
        assertThat(payload).isPresent();
        try (InputStream stream = payload.get().newStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Unable to read payload", e);
        }
    }

    private static final class RecordingSigner<T extends Identity> implements HttpSigner<T> {
        private SignRequest<? extends T> lastSignRequest;
        private AsyncSignRequest<? extends T> lastAsyncSignRequest;

        @Override
        public SignedRequest sign(SignRequest<? extends T> request) {
            lastSignRequest = request;
            SdkHttpRequest signedRequest = request.request().toBuilder()
                    .putHeader("X-Recorded-Signer", "sync")
                    .build();
            SignedRequest.Builder builder = SignedRequest.builder().request(signedRequest);
            request.payload().ifPresent(builder::payload);
            return builder.build();
        }

        @Override
        public CompletableFuture<AsyncSignedRequest> signAsync(AsyncSignRequest<? extends T> request) {
            lastAsyncSignRequest = request;
            SdkHttpRequest signedRequest = request.request().toBuilder()
                    .putHeader("X-Recorded-Signer", "async")
                    .build();
            AsyncSignedRequest.Builder builder = AsyncSignedRequest.builder().request(signedRequest);
            request.payload().ifPresent(builder::payload);
            return CompletableFuture.completedFuture(builder.build());
        }
    }

    private static final class TokenIdentityProvider implements IdentityProvider<TokenIdentity> {
        private final TokenIdentity identity;

        private TokenIdentityProvider(TokenIdentity identity) {
            this.identity = identity;
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }

        @Override
        public CompletableFuture<TokenIdentity> resolveIdentity(ResolveIdentityRequest request) {
            return CompletableFuture.completedFuture(identity);
        }
    }

    private static final class StaticAuthScheme implements AuthScheme<TokenIdentity> {
        private final String schemeId;
        private final IdentityProvider<TokenIdentity> provider;
        private final HttpSigner<TokenIdentity> signer;

        private StaticAuthScheme(String schemeId, IdentityProvider<TokenIdentity> provider,
                HttpSigner<TokenIdentity> signer) {
            this.schemeId = schemeId;
            this.provider = provider;
            this.signer = signer;
        }

        @Override
        public String schemeId() {
            return schemeId;
        }

        @Override
        public IdentityProvider<TokenIdentity> identityProvider(IdentityProviders providers) {
            assertThat(providers.identityProvider(TokenIdentity.class)).isSameAs(provider);
            return provider;
        }

        @Override
        public HttpSigner<TokenIdentity> signer() {
            return signer;
        }
    }

    private static final class FixedPublisher implements Publisher<ByteBuffer> {
        private final byte[] bytes;

        private FixedPublisher(String value) {
            this.bytes = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                private boolean done;

                @Override
                public void request(long count) {
                    if (!done && count > 0) {
                        done = true;
                        subscriber.onNext(ByteBuffer.wrap(bytes).asReadOnlyBuffer());
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }
}
