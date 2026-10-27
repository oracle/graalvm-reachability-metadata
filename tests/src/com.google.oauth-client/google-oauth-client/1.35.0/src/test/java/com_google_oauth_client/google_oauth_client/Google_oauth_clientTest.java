/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_oauth_client.google_oauth_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.api.client.auth.oauth.OAuthAuthorizeTemporaryTokenUrl;
import com.google.api.client.auth.oauth.OAuthCallbackUrl;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth.OAuthGetTemporaryToken;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.AuthorizationRequestUrl;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientCredentialsTokenRequest;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.PasswordTokenRequest;
import com.google.api.client.auth.oauth2.RefreshTokenRequest;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.auth.openidconnect.HttpTransportFactory;
import com.google.api.client.auth.openidconnect.IdToken;
import com.google.api.client.auth.openidconnect.IdTokenResponse;
import com.google.api.client.auth.openidconnect.IdTokenVerifier;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Clock;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

public class Google_oauth_clientTest {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final GenericUrl TOKEN_URL = new GenericUrl("https://issuer.example/token");

    @Test
    void parsesTokenModelsAndBuildsAuthorizationUrls() throws Exception {
        TokenResponse response =
                JSON_FACTORY.fromString(
                        """
                        {
                          "access_token": "access-1",
                          "token_type": "Bearer",
                          "expires_in": 3600,
                          "refresh_token": "refresh-1",
                          "scope": "read write",
                          "extension": "retained"
                        }
                        """,
                        TokenResponse.class);
        TokenErrorResponse error =
                JSON_FACTORY.fromString(
                        """
                        {
                          "error": "invalid_grant",
                          "error_description": "The grant is invalid",
                          "error_uri": "https://issuer.example/errors/invalid-grant"
                        }
                        """,
                        TokenErrorResponse.class);

        assertThat(response.getAccessToken()).isEqualTo("access-1");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(3600L);
        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
        assertThat(response.getScope()).isEqualTo("read write");
        assertThat(response.get("extension")).isEqualTo("retained");
        assertThat(error.getError()).isEqualTo("invalid_grant");
        assertThat(error.getErrorDescription()).isEqualTo("The grant is invalid");
        assertThat(error.getErrorUri()).endsWith("invalid-grant");

        AuthorizationRequestUrl implicitUrl =
                new AuthorizationRequestUrl(
                                "https://issuer.example/authorize",
                                "web-client",
                                Arrays.asList("token", "custom"))
                        .setRedirectUri("https://app.example/callback")
                        .setScopes(Arrays.asList("profile", "email"))
                        .setState("state-value");
        AuthorizationCodeRequestUrl codeUrl =
                new AuthorizationCodeRequestUrl("https://issuer.example/authorize", "web-client")
                        .setRedirectUri("https://app.example/callback")
                        .setScopes(Arrays.asList("openid", "profile"))
                        .setState("code-state");
        codeUrl.setCodeChallenge("challenge-value");
        codeUrl.setCodeChallengeMethod("S256");

        assertThat(implicitUrl.build()).contains("response_type=token%20custom");
        assertThat(implicitUrl.getScopes()).isEqualTo("profile email");
        assertThat(codeUrl.build())
                .contains("response_type=code")
                .contains("scope=openid%20profile")
                .contains("code_challenge=challenge-value")
                .contains("code_challenge_method=S256");
    }

    @Test
    void executesEveryTokenGrantAndClientAuthenticationMethod() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.enqueue(200, tokenJson("authorization-access", "authorization-refresh"));
        transport.enqueue(200, tokenJson("refresh-access", null));
        transport.enqueue(200, tokenJson("client-access", null));
        transport.enqueue(200, tokenJson("password-access", null));

        TokenResponse authorization =
                new AuthorizationCodeTokenRequest(
                                transport, JSON_FACTORY, TOKEN_URL, "authorization-code")
                        .setRedirectUri("https://app.example/callback")
                        .setScopes(Arrays.asList("read", "write"))
                        .setClientAuthentication(
                                new BasicAuthentication("basic-client", "basic-secret"))
                        .execute();
        TokenResponse refresh =
                new RefreshTokenRequest(transport, JSON_FACTORY, TOKEN_URL, "refresh-value")
                        .setClientAuthentication(
                                new ClientParametersAuthentication("form-client", "form-secret"))
                        .execute();
        TokenResponse client =
                new ClientCredentialsTokenRequest(transport, JSON_FACTORY, TOKEN_URL)
                        .setScopes(Arrays.asList("service.read"))
                        .execute();
        TokenResponse password =
                new PasswordTokenRequest(
                                transport,
                                JSON_FACTORY,
                                TOKEN_URL,
                                "resource-owner",
                                "password-value")
                        .setClientAuthentication(
                                new BasicAuthentication("password-client", "secret"))
                        .execute();

        assertThat(authorization.getAccessToken()).isEqualTo("authorization-access");
        assertThat(refresh.getAccessToken()).isEqualTo("refresh-access");
        assertThat(client.getAccessToken()).isEqualTo("client-access");
        assertThat(password.getAccessToken()).isEqualTo("password-access");

        assertThat(form(transport.requests.get(0).body))
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "authorization-code")
                .containsEntry("redirect_uri", "https://app.example/callback")
                .containsEntry("scope", "read write");
        assertThat(decodeBasic(transport.requests.get(0).header("Authorization")))
                .isEqualTo("basic-client:basic-secret");
        assertThat(form(transport.requests.get(1).body))
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "refresh-value")
                .containsEntry("client_id", "form-client")
                .containsEntry("client_secret", "form-secret");
        assertThat(form(transport.requests.get(2).body))
                .containsEntry("grant_type", "client_credentials")
                .containsEntry("scope", "service.read");
        assertThat(form(transport.requests.get(3).body))
                .containsEntry("grant_type", "password")
                .containsEntry("username", "resource-owner")
                .containsEntry("password", "password-value");
        assertThat(decodeBasic(transport.requests.get(3).header("Authorization")))
                .isEqualTo("password-client:secret");
    }

    @Test
    void parsesStructuredTokenEndpointErrors() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.enqueue(
                400,
                """
                {
                  "error": "invalid_client",
                  "error_description": "Client authentication failed",
                  "error_uri": "https://issuer.example/errors/client"
                }
                """);

        TokenResponseException failure = null;
        try {
            new ClientCredentialsTokenRequest(transport, JSON_FACTORY, TOKEN_URL).execute();
            fail("The token endpoint error should throw TokenResponseException");
        } catch (TokenResponseException expected) {
            failure = expected;
        }

        assertThat(failure).isNotNull();
        assertThat(failure.getStatusCode()).isEqualTo(400);
        assertThat(failure.getDetails().getError()).isEqualTo("invalid_client");
        assertThat(failure.getDetails().getErrorDescription())
                .isEqualTo("Client authentication failed");
        assertThat(failure.getDetails().getErrorUri()).endsWith("/client");
    }

    @Test
    void authorizationFlowPersistsLoadsAndRefreshesCredentials() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.enqueue(200, tokenJson("initial-access", "stored-refresh"));
        transport.enqueue(200, tokenJson("refreshed-access", null));
        Clock clock = new FixedClock(1_700_000_000_000L);
        AuthorizationCodeFlow flow =
                new AuthorizationCodeFlow.Builder(
                                BearerToken.authorizationHeaderAccessMethod(),
                                transport,
                                JSON_FACTORY,
                                TOKEN_URL,
                                new ClientParametersAuthentication("flow-client", "flow-secret"),
                                "flow-client",
                                "https://issuer.example/authorize")
                        .setClock(clock)
                        .setScopes(Arrays.asList("openid", "profile"))
                        .setDataStoreFactory(new MemoryDataStoreFactory())
                        .build();

        AuthorizationCodeRequestUrl authorizationUrl =
                flow.newAuthorizationUrl()
                        .setRedirectUri("https://app.example/callback")
                        .setState("flow-state");
        TokenResponse tokenResponse =
                flow.newTokenRequest("flow-code")
                        .setRedirectUri("https://app.example/callback")
                        .execute();
        Credential created = flow.createAndStoreCredential(tokenResponse, "user-1");
        Credential loaded = flow.loadCredential("user-1");

        assertThat(authorizationUrl.build())
                .contains("client_id=flow-client")
                .contains("scope=openid%20profile")
                .contains("state=flow-state");
        assertThat(created.getAccessToken()).isEqualTo("initial-access");
        assertThat(loaded.getAccessToken()).isEqualTo("initial-access");
        assertThat(loaded.getRefreshToken()).isEqualTo("stored-refresh");

        loaded.setExpiresInSeconds(0L);
        assertThat(loaded.refreshToken()).isTrue();
        assertThat(loaded.getAccessToken()).isEqualTo("refreshed-access");
        assertThat(loaded.getRefreshToken()).isEqualTo("stored-refresh");
        assertThat(form(transport.requests.get(1).body))
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "stored-refresh")
                .containsEntry("client_id", "flow-client");

        loaded.setExpiresInSeconds(3600L);
        HttpRequest protectedRequest =
                transport
                        .createRequestFactory()
                        .buildGetRequest(new GenericUrl("https://resource.example/profile"));
        loaded.intercept(protectedRequest);
        assertThat(protectedRequest.getHeaders().getAuthorization())
                .isEqualTo("Bearer refreshed-access");
    }

    @Test
    void parsesAndCryptographicallyVerifiesIdTokenResponseOffline() throws Exception {
        long nowMillis = 1_700_000_000_000L;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String idToken = signedIdToken(keyPair, nowMillis / 1000);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        RecordingTransport transport = new RecordingTransport();
        transport.enqueue(
                200,
                "{\"access_token\":\"openid-access\",\"token_type\":\"Bearer\","
                        + "\"id_token\":\""
                        + idToken
                        + "\"}");
        transport.enqueue(
                200,
                "{\"keys\":[{\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\","
                        + "\"kid\":\"key-1\",\"n\":\""
                        + unsignedBase64(publicKey.getModulus())
                        + "\",\"e\":\""
                        + unsignedBase64(publicKey.getPublicExponent())
                        + "\"}]}");

        IdTokenResponse response =
                IdTokenResponse.execute(
                        new ClientCredentialsTokenRequest(transport, JSON_FACTORY, TOKEN_URL));
        IdToken parsed = response.parseIdToken();
        IdTokenVerifier verifier =
                new IdTokenVerifier.Builder()
                        .setIssuer("https://issuer.example")
                        .setAudience(Arrays.asList("native-client"))
                        .setClock(new FixedClock(nowMillis))
                        .setAcceptableTimeSkewSeconds(30)
                        .setCertificatesLocation("https://issuer.example/keys")
                        .setHttpTransportFactory(new SingleTransportFactory(transport))
                        .build();

        assertThat(response.getAccessToken()).isEqualTo("openid-access");
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo("key-1");
        assertThat(parsed.getPayload().getIssuer()).isEqualTo("https://issuer.example");
        assertThat(parsed.getPayload().getSubject()).isEqualTo("subject-1");
        assertThat(verifier.verifyOrThrow(parsed)).isTrue();
        assertThat(transport.requests.get(1).url).isEqualTo("https://issuer.example/keys");
    }

    @Test
    void completesOAuthOneTemporaryTokenAndAccessTokenExchange() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.enqueue(
                200,
                "oauth_token=temporary-token&oauth_token_secret=temporary-secret"
                        + "&oauth_callback_confirmed=true");
        transport.enqueue(200, "oauth_token=access-token&oauth_token_secret=access-secret");

        OAuthHmacSigner temporaryTokenSigner = new OAuthHmacSigner();
        temporaryTokenSigner.clientSharedSecret = "consumer-secret";
        OAuthGetTemporaryToken temporaryTokenRequest =
                new OAuthGetTemporaryToken("https://issuer.example/request-token");
        temporaryTokenRequest.transport = transport;
        temporaryTokenRequest.consumerKey = "consumer-key";
        temporaryTokenRequest.signer = temporaryTokenSigner;
        temporaryTokenRequest.callback = "https://app.example/oauth/callback";

        OAuthCredentialsResponse temporaryCredentials = temporaryTokenRequest.execute();
        OAuthAuthorizeTemporaryTokenUrl authorizationUrl =
                new OAuthAuthorizeTemporaryTokenUrl("https://issuer.example/authorize");
        authorizationUrl.temporaryToken = temporaryCredentials.token;
        OAuthCallbackUrl callbackUrl =
                new OAuthCallbackUrl(
                        "https://app.example/oauth/callback?oauth_token=temporary-token"
                                + "&oauth_verifier=verification-code");

        OAuthHmacSigner accessTokenSigner = new OAuthHmacSigner();
        accessTokenSigner.clientSharedSecret = "consumer-secret";
        accessTokenSigner.tokenSharedSecret = temporaryCredentials.tokenSecret;
        OAuthGetAccessToken accessTokenRequest =
                new OAuthGetAccessToken("https://issuer.example/access-token");
        accessTokenRequest.transport = transport;
        accessTokenRequest.consumerKey = "consumer-key";
        accessTokenRequest.signer = accessTokenSigner;
        accessTokenRequest.temporaryToken = callbackUrl.token;
        accessTokenRequest.verifier = callbackUrl.verifier;

        OAuthCredentialsResponse accessCredentials = accessTokenRequest.execute();

        assertThat(temporaryCredentials.token).isEqualTo("temporary-token");
        assertThat(temporaryCredentials.tokenSecret).isEqualTo("temporary-secret");
        assertThat(temporaryCredentials.callbackConfirmed).isTrue();
        assertThat(authorizationUrl.build()).contains("oauth_token=temporary-token");
        assertThat(callbackUrl.token).isEqualTo("temporary-token");
        assertThat(callbackUrl.verifier).isEqualTo("verification-code");
        assertThat(accessCredentials.token).isEqualTo("access-token");
        assertThat(accessCredentials.tokenSecret).isEqualTo("access-secret");

        String temporaryAuthorization = transport.requests.get(0).header("Authorization");
        assertThat(temporaryAuthorization)
                .startsWith("OAuth ")
                .contains("oauth_consumer_key=\"consumer-key\"")
                .contains("oauth_callback=\"https%3A%2F%2Fapp.example%2Foauth%2Fcallback\"")
                .contains("oauth_signature_method=\"HMAC-SHA1\"")
                .contains("oauth_signature=\"");
        String accessAuthorization = transport.requests.get(1).header("Authorization");
        assertThat(accessAuthorization)
                .startsWith("OAuth ")
                .contains("oauth_token=\"temporary-token\"")
                .contains("oauth_verifier=\"verification-code\"")
                .contains("oauth_signature_method=\"HMAC-SHA1\"")
                .contains("oauth_signature=\"");
    }

    private static String tokenJson(String accessToken, String refreshToken) {
        String refresh =
                refreshToken == null ? "" : ",\"refresh_token\":\"" + refreshToken + "\"";
        return "{\"access_token\":\""
                + accessToken
                + "\",\"token_type\":\"Bearer\",\"expires_in\":3600"
                + refresh
                + "}";
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String decodeBasic(String authorization) {
        assertThat(authorization).startsWith("Basic ");
        return new String(
                Base64.getDecoder().decode(authorization.substring("Basic ".length())),
                StandardCharsets.UTF_8);
    }

    private static String signedIdToken(KeyPair keyPair, long nowSeconds) throws Exception {
        String header =
                base64Url("{\"alg\":\"RS256\",\"kid\":\"key-1\",\"typ\":\"JWT\"}");
        String payload =
                base64Url(
                        "{\"iss\":\"https://issuer.example\",\"aud\":\"native-client\","
                                + "\"sub\":\"subject-1\",\"iat\":"
                                + nowSeconds
                                + ",\"exp\":"
                                + (nowSeconds + 3600)
                                + "}");
        String signingInput = header + "." + payload;
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unsignedBase64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Arrays.copyOfRange(bytes, offset, bytes.length));
    }

    private static final class FixedClock implements Clock {
        private final long currentTimeMillis;

        private FixedClock(long currentTimeMillis) {
            this.currentTimeMillis = currentTimeMillis;
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
        }
    }

    private static final class SingleTransportFactory implements HttpTransportFactory {
        private final HttpTransport transport;

        private SingleTransportFactory(HttpTransport transport) {
            this.transport = transport;
        }

        @Override
        public HttpTransport create() {
            return transport;
        }
    }

    private static final class RecordingTransport extends HttpTransport {
        private final Queue<ResponseSpec> responses = new ArrayDeque<>();
        private final List<RecordedRequest> requests = new ArrayList<>();

        private void enqueue(int statusCode, String body) {
            responses.add(new ResponseSpec(statusCode, body));
        }

        @Override
        protected LowLevelHttpRequest buildRequest(String method, String url) {
            return new RecordingRequest(this, method, url);
        }
    }

    private static final class RecordingRequest extends LowLevelHttpRequest {
        private final RecordingTransport transport;
        private final RecordedRequest request;

        private RecordingRequest(RecordingTransport transport, String method, String url) {
            this.transport = transport;
            request = new RecordedRequest(method, url);
        }

        @Override
        public void addHeader(String name, String value) {
            request.headers.put(name.toLowerCase(Locale.ROOT), value);
        }

        @Override
        public LowLevelHttpResponse execute() throws IOException {
            if (getStreamingContent() != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                getStreamingContent().writeTo(output);
                request.body = output.toString(StandardCharsets.UTF_8);
            }
            transport.requests.add(request);
            ResponseSpec response = transport.responses.poll();
            if (response == null) {
                throw new IOException(
                        "No response was queued for " + request.method + " " + request.url);
            }
            return new StaticResponse(response.statusCode, response.body);
        }
    }

    private static final class StaticResponse extends LowLevelHttpResponse {
        private final int statusCode;
        private final byte[] content;

        private StaticResponse(int statusCode, String content) {
            this.statusCode = statusCode;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getContent() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public String getContentEncoding() {
            return null;
        }

        @Override
        public long getContentLength() {
            return content.length;
        }

        @Override
        public String getContentType() {
            return "application/json; charset=UTF-8";
        }

        @Override
        public String getStatusLine() {
            return "HTTP/1.1 " + statusCode + " " + getReasonPhrase();
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getReasonPhrase() {
            return statusCode >= 200 && statusCode < 300 ? "OK" : "Bad Request";
        }

        @Override
        public int getHeaderCount() {
            return 0;
        }

        @Override
        public String getHeaderName(int index) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }

        @Override
        public String getHeaderValue(int index) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
    }

    private static final class ResponseSpec {
        private final int statusCode;
        private final String body;

        private ResponseSpec(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static final class RecordedRequest {
        private final String method;
        private final String url;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String body = "";

        private RecordedRequest(String method, String url) {
            this.method = method;
            this.url = url;
        }

        private String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
