/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.http_auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import software.amazon.awssdk.http.auth.scheme.BearerAuthScheme;
import software.amazon.awssdk.http.auth.scheme.NoAuthAuthScheme;
import software.amazon.awssdk.http.auth.signer.BearerHttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignRequest;
import software.amazon.awssdk.http.auth.spi.signer.AsyncSignedRequest;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignRequest;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.identity.spi.IdentityProviders;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;
import software.amazon.awssdk.identity.spi.TokenIdentity;

public class Http_authTest {
    @Test
    void bearerAuthSchemeUsesRegisteredTokenProviderAndDefaultSigner() throws Exception {
        TokenIdentity tokenIdentity = TokenIdentity.create("scheme-token");
        TokenIdentityProvider tokenProvider = new TokenIdentityProvider(tokenIdentity);
        IdentityProviders identityProviders = IdentityProviders.builder()
                .putIdentityProvider(tokenProvider)
                .build();

        BearerAuthScheme authScheme = BearerAuthScheme.create();

        assertThat(authScheme.schemeId()).isEqualTo(BearerAuthScheme.SCHEME_ID);
        assertThat(authScheme.schemeId()).isEqualTo("smithy.api#httpBearerAuth");
        assertThat(authScheme.identityProvider(identityProviders)).isSameAs(tokenProvider);
        assertThat(authScheme.identityProvider(identityProviders).resolveIdentity().get(5, TimeUnit.SECONDS))
                .isSameAs(tokenIdentity);
        assertThat(authScheme.signer()).isNotNull();
    }

    @Test
    void bearerSignerAddsAuthorizationHeaderAndPreservesPayload() {
        BearerHttpSigner signer = BearerHttpSigner.create();
        TokenIdentity identity = TokenIdentity.create("access-token");
        SdkHttpFullRequest request = httpRequest("/bearer-sync")
                .toBuilder()
                .putHeader("Authorization", "old value")
                .build();
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("sync-body");

        SignedRequest signedRequest = signer.sign(SignRequest.builder(identity)
                .request(request)
                .payload(payload)
                .build());

        assertThat(signedRequest.request()).isNotSameAs(request);
        assertThat(signedRequest.request().firstMatchingHeader("Authorization")).contains("Bearer access-token");
        assertThat(signedRequest.request().encodedPath()).isEqualTo("/bearer-sync");
        assertThat(signedRequest.request().firstMatchingRawQueryParameter("mode")).contains("test");
        assertThat(signedRequest.payload()).containsSame(payload);
        assertThat(readUtf8(signedRequest.payload())).isEqualTo("sync-body");
        assertThat(request.firstMatchingHeader("Authorization")).contains("old value");
    }

    @Test
    void bearerSignerReplacesAuthorizationHeaderCaseInsensitively() {
        BearerHttpSigner signer = BearerHttpSigner.create();
        TokenIdentity identity = TokenIdentity.create("case-token");
        SdkHttpFullRequest request = httpRequest("/bearer-case-insensitive")
                .toBuilder()
                .putHeader("authorization", List.of("old value", "second old value"))
                .build();

        SignedRequest signedRequest = signer.sign(SignRequest.builder(identity)
                .request(request)
                .build());

        assertThat(signedRequest.request().matchingHeaders("Authorization")).containsExactly("Bearer case-token");
    }

    @Test
    void bearerSignerSupportsConsumerStyleRequestsAndAsyncPayloads() throws Exception {
        BearerHttpSigner signer = BearerHttpSigner.create();
        TokenIdentity syncIdentity = TokenIdentity.create("consumer-token");
        ContentStreamProvider syncPayload = ContentStreamProvider.fromUtf8String("consumer-body");

        SignedRequest syncSignedRequest = signer.sign(builder -> builder
                .identity(syncIdentity)
                .request(httpRequest("/bearer-consumer"))
                .payload(syncPayload));

        assertThat(syncSignedRequest.request().firstMatchingHeader("Authorization")).contains("Bearer consumer-token");
        assertThat(syncSignedRequest.request().encodedPath()).isEqualTo("/bearer-consumer");
        assertThat(syncSignedRequest.payload()).containsSame(syncPayload);

        FixedPublisher asyncPayload = new FixedPublisher("async-body");
        AsyncSignedRequest asyncSignedRequest = signer.signAsync(builder -> builder
                        .identity(TokenIdentity.create("async-token"))
                        .request(httpRequest("/bearer-async"))
                        .payload(asyncPayload))
                .get(5, TimeUnit.SECONDS);

        assertThat(asyncSignedRequest.request().firstMatchingHeader("Authorization")).contains("Bearer async-token");
        assertThat(asyncSignedRequest.request().encodedPath()).isEqualTo("/bearer-async");
        assertThat(asyncSignedRequest.payload()).containsSame(asyncPayload);

        SignedRequest withoutPayload = signer.sign(SignRequest.builder(TokenIdentity.create("no-payload-token"))
                .request(httpRequest("/bearer-no-payload"))
                .build());
        assertThat(withoutPayload.request().firstMatchingHeader("Authorization")).contains("Bearer no-payload-token");
        assertThat(withoutPayload.payload()).isEmpty();
    }

    @Test
    void bearerSignerCanCreateIdentityAgnosticNoOpSigner() throws Exception {
        HttpSigner<NoAuthAuthScheme.AnonymousIdentity> unsignedSigner = BearerHttpSigner.create().doNotSign();
        NoAuthAuthScheme.AnonymousIdentity anonymousIdentity = new TestAnonymousIdentity();
        SdkHttpFullRequest request = httpRequest("/bearer-do-not-sign")
                .toBuilder()
                .putHeader("Authorization", "existing value")
                .build();
        ContentStreamProvider syncPayload = ContentStreamProvider.fromUtf8String("unsigned-body");

        SignedRequest signedRequest = unsignedSigner.sign(SignRequest.builder(anonymousIdentity)
                .request(request)
                .payload(syncPayload)
                .build());

        assertThat(signedRequest.request()).isSameAs(request);
        assertThat(signedRequest.request().firstMatchingHeader("Authorization")).contains("existing value");
        assertThat(signedRequest.payload()).containsSame(syncPayload);
        assertThat(readUtf8(signedRequest.payload())).isEqualTo("unsigned-body");

        FixedPublisher asyncPayload = new FixedPublisher("unsigned-async-body");
        AsyncSignedRequest asyncSignedRequest = unsignedSigner.signAsync(AsyncSignRequest.builder(anonymousIdentity)
                        .request(request)
                        .payload(asyncPayload)
                        .build())
                .get(5, TimeUnit.SECONDS);

        assertThat(asyncSignedRequest.request()).isSameAs(request);
        assertThat(asyncSignedRequest.request().firstMatchingHeader("Authorization")).contains("existing value");
        assertThat(asyncSignedRequest.payload()).containsSame(asyncPayload);
    }

    @Test
    void noAuthSchemeProvidesAnonymousIdentityAndNoOpSigner() throws Exception {
        NoAuthAuthScheme authScheme = NoAuthAuthScheme.create();
        IdentityProviders emptyProviders = IdentityProviders.builder().build();

        IdentityProvider<NoAuthAuthScheme.AnonymousIdentity> identityProvider =
                authScheme.identityProvider(emptyProviders);
        NoAuthAuthScheme.AnonymousIdentity identity = identityProvider.resolveIdentity().get(5, TimeUnit.SECONDS);

        assertThat(authScheme.schemeId()).isEqualTo(NoAuthAuthScheme.SCHEME_ID);
        assertThat(authScheme.schemeId()).isEqualTo("smithy.api#noAuth");
        assertThat(identityProvider.identityType()).isEqualTo(NoAuthAuthScheme.AnonymousIdentity.class);
        assertThat(identity).isSameAs(identityProvider.resolveIdentity(builder -> { }).get(5, TimeUnit.SECONDS));

        SdkHttpFullRequest request = httpRequest("/no-auth");
        ContentStreamProvider payload = ContentStreamProvider.fromUtf8String("anonymous-body");
        SignedRequest signedRequest = authScheme.signer().sign(SignRequest.builder(identity)
                .request(request)
                .payload(payload)
                .build());

        assertThat(signedRequest.request()).isSameAs(request);
        assertThat(signedRequest.request().firstMatchingHeader("Authorization")).isEmpty();
        assertThat(signedRequest.payload()).containsSame(payload);
        assertThat(readUtf8(signedRequest.payload())).isEqualTo("anonymous-body");
    }

    @Test
    void noAuthSignerPreservesAsyncRequestAndPayload() throws Exception {
        NoAuthAuthScheme authScheme = NoAuthAuthScheme.create();
        NoAuthAuthScheme.AnonymousIdentity identity = authScheme.identityProvider(IdentityProviders.builder().build())
                .resolveIdentity(ResolveIdentityRequest.builder().build())
                .get(5, TimeUnit.SECONDS);
        SdkHttpFullRequest request = httpRequest("/no-auth-async");
        FixedPublisher payload = new FixedPublisher("anonymous-async-body");

        AsyncSignedRequest signedRequest = authScheme.signer().signAsync(builder -> builder
                        .identity(identity)
                        .request(request)
                        .payload(payload))
                .get(5, TimeUnit.SECONDS);

        assertThat(signedRequest.request()).isSameAs(request);
        assertThat(signedRequest.request().firstMatchingHeader("Authorization")).isEmpty();
        assertThat(signedRequest.payload()).containsSame(payload);

        AsyncSignedRequest withoutPayload = authScheme.signer().signAsync(builder -> builder
                        .identity(identity)
                        .request(httpRequest("/no-auth-async-no-payload")))
                .get(5, TimeUnit.SECONDS);
        assertThat(withoutPayload.request().encodedPath()).isEqualTo("/no-auth-async-no-payload");
        assertThat(withoutPayload.payload()).isEmpty();
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

    private static final class TestAnonymousIdentity implements NoAuthAuthScheme.AnonymousIdentity {
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
