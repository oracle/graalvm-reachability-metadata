/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadata;
import io.micronaut.security.oauth2.client.clientcredentials.propagation.ClientCredentialsTokenPropagator;
import io.micronaut.security.oauth2.client.clientcredentials.propagation.DefaultClientCredentialsTokenPropagator;
import io.micronaut.security.oauth2.configuration.OauthClientConfiguration;
import io.micronaut.security.oauth2.configuration.OauthConfigurationProperties;
import io.micronaut.security.oauth2.configuration.endpoints.SecureEndpointConfiguration;
import io.micronaut.security.oauth2.endpoint.AuthenticationMethods;
import io.micronaut.security.oauth2.endpoint.SecureEndpoint;
import io.micronaut.security.oauth2.endpoint.authorization.pkce.Pkce;
import io.micronaut.security.oauth2.endpoint.authorization.pkce.PkceConfigurationProperties;
import io.micronaut.security.oauth2.endpoint.authorization.pkce.PkceGenerator;
import io.micronaut.security.oauth2.endpoint.authorization.pkce.S256PkceGenerator;
import io.micronaut.security.oauth2.endpoint.authorization.request.AuthorizationRequest;
import io.micronaut.security.oauth2.endpoint.authorization.request.DefaultAuthorizationRedirectHandler;
import io.micronaut.security.oauth2.endpoint.authorization.state.DefaultState;
import io.micronaut.security.oauth2.endpoint.token.response.Address;
import io.micronaut.security.oauth2.endpoint.token.response.OpenIdTokenResponse;
import io.micronaut.security.oauth2.endpoint.token.response.TokenError;
import io.micronaut.security.oauth2.endpoint.token.response.TokenErrorResponse;
import io.micronaut.security.oauth2.grants.AuthorizationCodeGrant;
import io.micronaut.security.oauth2.grants.ClientCredentialsGrant;
import io.micronaut.security.oauth2.grants.GrantType;
import io.micronaut.security.oauth2.metadata.ProtectedResourceMetadata;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for OAuth 2.0 configuration and protocol value APIs. */
@Timeout(55)
public class Micronaut_security_oauth2Test {
    @Test
    void applicationContextBindsOauthConfigurationAndCreatesPkceGenerators() {
        Map<String, Object> properties = Map.of(
                "micronaut.security.oauth2.enabled", true,
                "micronaut.security.oauth2.login-uri", "/auth/login{/provider}",
                "micronaut.security.oauth2.callback-uri", "/auth/callback{/provider}",
                "micronaut.security.oauth2.default-provider", "company",
                "micronaut.security.oauth2.openid.logout-uri", "/auth/logout",
                "micronaut.security.oauth2.openid.end-session.redirect-uri", "/signed-out",
                "micronaut.security.oauth2.pkce.entropy", 32);

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            OauthConfigurationProperties configuration = context.getBean(OauthConfigurationProperties.class);
            assertThat(configuration.isEnabled()).isTrue();
            assertThat(configuration.getLoginUri()).isEqualTo("/auth/login{/provider}");
            assertThat(configuration.getCallbackUri()).isEqualTo("/auth/callback{/provider}");
            assertThat(configuration.getDefaultProvider()).contains("company");
            assertThat(configuration.getOpenid().getLogoutUri()).isEqualTo("/auth/logout");
            assertThat(configuration.getOpenid().getEndSession().orElseThrow().getRedirectUri())
                    .isEqualTo("/signed-out");

            PkceConfigurationProperties pkceConfiguration = context.getBean(PkceConfigurationProperties.class);
            assertThat(pkceConfiguration.getEntropy()).isEqualTo(32);
            assertThat(pkceConfiguration.isEnabled()).isTrue();

            PkceGenerator s256 = context.getBeansOfType(PkceGenerator.class).stream()
                    .filter(generator -> S256PkceGenerator.CODE_CHALLENGE_METHOD_S256.equals(generator.getName()))
                    .findFirst()
                    .orElseThrow();
            Pkce generated = s256.generate();
            assertThat(generated.getCodeChallengeMethod()).isEqualTo("S256");
            assertThat(generated.getCodeVerifier()).hasSize(43);
            assertThat(generated.getCodeChallenge())
                    .isEqualTo(S256PkceGenerator.hash(generated.getCodeVerifier()))
                    .doesNotContain("=");
        }
    }

    @Test
    void programmaticClientConfigurationPreservesEndpointsAndGrantPolicy() {
        OauthClientConfiguration client = OauthClientConfiguration.builder()
                .name("company")
                .clientId("client-id")
                .clientSecret("client-secret")
                .scopes("messages:read", "messages:write")
                .authorization("https://identity.example.test/authorize", "S256")
                .token(SecureEndpointConfiguration.builder()
                        .url("https://identity.example.test/token")
                        .authenticationMethod(AuthenticationMethods.CLIENT_SECRET_BASIC)
                        .build())
                .introspection(
                        "https://identity.example.test/introspect",
                        AuthenticationMethods.CLIENT_SECRET_BASIC)
                .revocation(
                        "https://identity.example.test/revoke",
                        AuthenticationMethods.CLIENT_SECRET_POST)
                .grantType(GrantType.CLIENT_CREDENTIALS)
                .proxyWellKnownOauthAuthorizationServer(true)
                .build();

        assertThat(client.getName()).isEqualTo("company");
        assertThat(client.getClientId()).isEqualTo("client-id");
        assertThat(client.getClientSecret()).isEqualTo("client-secret");
        assertThat(client.getScopes()).containsExactly("messages:read", "messages:write");
        assertThat(client.getGrantType()).isEqualTo(GrantType.CLIENT_CREDENTIALS);
        assertThat(client.getAuthorization().orElseThrow().getCodeChallengeMethod()).contains("S256");
        assertThat(client.getIntrospection().orElseThrow().getUrl())
                .contains("https://identity.example.test/introspect");
        assertThat(client.getRevocation().orElseThrow().getAuthenticationMethod())
                .contains(AuthenticationMethods.CLIENT_SECRET_POST);
        assertThat(client.isProxyWellKnownOauthAuthorizationServer()).isTrue();

        SecureEndpoint tokenEndpoint = client.getTokenEndpoint();
        assertThat(tokenEndpoint.getUrl()).isEqualTo("https://identity.example.test/token");
        assertThat(tokenEndpoint.getAuthenticationMethodsSupported())
                .containsExactly(AuthenticationMethods.CLIENT_SECRET_BASIC);
    }

    @Test
    void authorizationRedirectHandlerBuildsAuthorizationRequestLocation() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest() {
            @Override
            public List<String> getScopes() {
                return List.of("openid");
            }

            @Override
            public String getClientId() {
                return "browser-client";
            }

            @Override
            public Optional<String> getState(MutableHttpResponse<?> response) {
                return Optional.of("request-state");
            }

            @Override
            public String getResponseType() {
                return "code";
            }

            @Override
            public Optional<String> getRedirectUri() {
                return Optional.of("https://app.example.test/callback");
            }
        };

        MutableHttpResponse<?> response = new DefaultAuthorizationRedirectHandler()
                .redirect(authorizationRequest, "https://identity.example.test/authorize");

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.FOUND.getCode());
        URI location = URI.create(response.getHeaders().get(HttpHeaders.LOCATION));
        assertThat(location.getScheme()).isEqualTo("https");
        assertThat(location.getAuthority()).isEqualTo("identity.example.test");
        assertThat(location.getPath()).isEqualTo("/authorize");
        assertThat(location.getQuery())
                .contains("scope=openid")
                .contains("response_type=code")
                .contains("client_id=browser-client")
                .contains("redirect_uri=https://app.example.test/callback")
                .contains("state=request-state");
    }

    @Test
    void grantObjectsProduceRfcCompatibleTokenRequestParameters() {
        AuthorizationCodeGrant authorizationCode = new AuthorizationCodeGrant();
        authorizationCode.setClientId("browser-client");
        authorizationCode.setClientSecret("browser-secret");
        authorizationCode.setCode("authorization-code");
        authorizationCode.setRedirectUri("https://app.example.test/callback");
        authorizationCode.setCodeVerifier("pkce-verifier");

        assertThat(authorizationCode.toMap())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", "browser-client",
                        "client_secret", "browser-secret",
                        "code", "authorization-code",
                        "redirect_uri", "https://app.example.test/callback",
                        "code_verifier", "pkce-verifier"));

        ClientCredentialsGrant clientCredentials = new ClientCredentialsGrant();
        clientCredentials.setClientId("service-client");
        clientCredentials.setClientSecret("service-secret");
        clientCredentials.setScope("catalog:read catalog:write");

        assertThat(clientCredentials.toMap())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "service-client",
                        "client_secret", "service-secret",
                        "scope", "catalog:read catalog:write"));
    }

    @Test
    void clientCredentialsPropagatorWritesAndRecoversBearerToken() {
        ClientCredentialsTokenPropagator propagator = new DefaultClientCredentialsTokenPropagator();
        MutableHttpRequest<?> request = HttpRequest.GET("https://inventory.example.test/items");

        propagator.writeToken(request, "service-access-token");

        assertThat(request.getHeaders().get(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer service-access-token");
        assertThat(propagator.findToken(request)).contains("service-access-token");
    }

    @Test
    void openIdProviderMetadataBuilderRepresentsDiscoveryDocument() {
        DefaultOpenIdProviderMetadata metadata = DefaultOpenIdProviderMetadata.builder("company")
                .issuer("https://identity.example.test")
                .authorizationEndpoint("https://identity.example.test/authorize")
                .tokenEndpoint("https://identity.example.test/token")
                .userinfoEndpoint("https://identity.example.test/userinfo")
                .jwksUri("https://identity.example.test/jwks")
                .idTokenSigningAlgValuesSupported(List.of("RS256"))
                .subjectTypesSupported(List.of("public"))
                .responseTypesSupported(List.of("code"))
                .scopesSupported(List.of("openid", "profile"))
                .grantTypesSupported(List.of("authorization_code", "refresh_token"))
                .tokenEndpointAuthMethodsSupported(List.of("client_secret_basic"))
                .codeChallengeMethodsSupported(List.of("S256"))
                .endSessionEndpoint("https://identity.example.test/logout")
                .claimsSupported(List.of("sub", "email"))
                .build();

        assertThat(metadata.getName()).isEqualTo("company");
        assertThat(metadata.getIssuer()).isEqualTo("https://identity.example.test");
        assertThat(metadata.getAuthorizationEndpoint())
                .isEqualTo("https://identity.example.test/authorize");
        assertThat(metadata.getTokenEndpoint()).isEqualTo("https://identity.example.test/token");
        assertThat(metadata.getIdTokenSigningAlgValuesSupported()).containsExactly("RS256");
        assertThat(metadata.getScopesSupported()).containsExactly("openid", "profile");
        assertThat(metadata.getCodeChallengeMethodsSupported()).containsExactly("S256");
        assertThat(metadata.getEndSessionEndpoint()).isEqualTo("https://identity.example.test/logout");
    }

    @Test
    void protectedResourceMetadataBuilderRetainsRfc9728Capabilities() {
        ProtectedResourceMetadata metadata = ProtectedResourceMetadata.builder()
                .resource("https://api.example.test")
                .authorizationServer("https://identity.example.test")
                .jwksUri("https://api.example.test/jwks")
                .scopesSupported(List.of("catalog:read", "catalog:write"))
                .bearerMethodsSupported(List.of("header"))
                .resourceSigningAlgValuesSupported(List.of("RS256"))
                .resourceName("Catalog API")
                .resourceDocumentation("https://api.example.test/docs")
                .tlsClientCertificateBoundAccessTokens(true)
                .authorizationDetailsTypesSupported(List.of("payment_initiation"))
                .dpopSigningAlgValuesSupported(List.of("ES256"))
                .dpopBoundAccessTokensRequired(false)
                .build();

        assertThat(metadata.resource()).isEqualTo("https://api.example.test");
        assertThat(metadata.authorizationServers()).containsExactly("https://identity.example.test");
        assertThat(metadata.scopesSupported()).containsExactly("catalog:read", "catalog:write");
        assertThat(metadata.bearerMethodsSupported()).containsExactly("header");
        assertThat(metadata.resourceName()).isEqualTo("Catalog API");
        assertThat(metadata.tlsClientCertificateBoundAccessTokens()).isTrue();
        assertThat(metadata.dpopSigningAlgValuesSupported()).containsExactly("ES256");
        assertThat(metadata.dpopBoundAccessTokensRequired()).isFalse();
    }

    @Test
    void responseStateAndClaimsModelsExposeProtocolValues() {
        OpenIdTokenResponse response = new OpenIdTokenResponse();
        response.setAccessToken("access-token");
        response.setTokenType("Bearer");
        response.setExpiresIn(300);
        response.setRefreshToken("refresh-token");
        response.setScope("openid profile");
        response.setIdToken("id-token");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(300);
        assertThat(response.getExpiresInDate()).isPresent();
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getScope()).isEqualTo("openid profile");
        assertThat(response.getIdToken()).isEqualTo("id-token");

        Address address = new Address(Map.of(
                Address.STREET_ADDRESS, "1 Main Street",
                Address.LOCALITY, "Example City",
                Address.REGION, "CA",
                Address.POSTAL_CODE, "90210",
                Address.COUNTRY, "US"));
        assertThat(address.getStreetAddress()).isEqualTo("1 Main Street");
        assertThat(address.getLocality()).isEqualTo("Example City");
        assertThat(address.getRegion()).isEqualTo("CA");
        assertThat(address.getPostalCode()).isEqualTo("90210");
        assertThat(address.getCountry()).isEqualTo("US");

        DefaultState firstState = new DefaultState();
        firstState.setNonce("state-nonce");
        firstState.setRedirectUri(URI.create("https://app.example.test/after-login"));
        DefaultState matchingState = new DefaultState();
        matchingState.setNonce("state-nonce");
        assertThat(firstState).isEqualTo(matchingState);
        assertThat(firstState.getRedirectUri())
                .isEqualTo(URI.create("https://app.example.test/after-login"));

        TokenErrorResponse error = new TokenErrorResponse();
        error.setError(TokenError.INVALID_GRANT);
        error.setErrorDescription("The authorization code is no longer valid");
        error.setErrorUri("https://identity.example.test/errors/invalid-grant");
        assertThat(error.toString())
                .contains("invalid_grant")
                .contains("The authorization code is no longer valid")
                .contains("https://identity.example.test/errors/invalid-grant");
    }
}
