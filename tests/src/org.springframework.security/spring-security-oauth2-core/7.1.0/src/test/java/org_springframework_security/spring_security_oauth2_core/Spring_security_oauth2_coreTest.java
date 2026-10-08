/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_oauth2_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.converter.ClaimConversionService;
import org.springframework.security.oauth2.core.endpoint.DefaultMapOAuth2AccessTokenResponseConverter;
import org.springframework.security.oauth2.core.endpoint.DefaultOAuth2AccessTokenResponseMapConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

public class Spring_security_oauth2_coreTest {
    @Test
    void tokenTypesAndOAuth2ErrorsExposeTheirValues() {
        Instant issuedAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(900);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
                issuedAt, expiresAt, Set.of("read", "write"));
        OAuth2Error error = new OAuth2Error("invalid_grant", "The grant is not valid",
                "https://example.test/errors/invalid-grant");

        assertThat(accessToken.getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
        assertThat(accessToken.getTokenValue()).isEqualTo("access-token");
        assertThat(accessToken.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(accessToken.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(accessToken.getScopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(error.getErrorCode()).isEqualTo("invalid_grant");
        assertThat(error.getDescription()).isEqualTo("The grant is not valid");
        assertThat(error.toString()).contains("invalid_grant");
    }

    @Test
    void authorizationAndClientAuthenticationMethodsSupportStandardAndCustomValues() {
        assertThat(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()).isEqualTo("authorization_code");
        assertThat(AuthorizationGrantType.DEVICE_CODE.getValue())
                .isEqualTo("urn:ietf:params:oauth:grant-type:device_code");
        assertThat(ClientAuthenticationMethod.valueOf("client_secret_basic"))
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(new ClientAuthenticationMethod("custom_method").getValue()).isEqualTo("custom_method");
    }

    @Test
    void authorizationRequestAndResponseBuildersPreserveProtocolParameters() {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://issuer.example.test/oauth2/authorize").clientId("client-42")
                .redirectUri("https://app.example.test/callback").scope("openid", "profile").state("state-123")
                .additionalParameters(Map.of("prompt", "consent")).build();
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success("code-abc")
                .redirectUri(request.getRedirectUri()).state(request.getState()).build();

        assertThat(request.getGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(request.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(request.getAdditionalParameters()).containsEntry("prompt", "consent");
        assertThat(request.getAuthorizationRequestUri()).contains("client_id=client-42", "state=state-123");
        assertThat(response.statusOk()).isTrue();
        assertThat(response.getCode()).isEqualTo("code-abc");
        assertThat(response.getRedirectUri()).isEqualTo(request.getRedirectUri());
    }

    @Test
    void accessTokenResponseConvertersRoundTripTokenAndAdditionalParameters() {
        OAuth2AccessTokenResponse response = OAuth2AccessTokenResponse.withToken("token-7").tokenType(
                OAuth2AccessToken.TokenType.DPOP).expiresIn(120).scopes(Set.of("read"))
                .refreshToken("refresh-7").additionalParameters(Map.of("custom", "value")).build();

        Map<String, Object> encoded = new DefaultOAuth2AccessTokenResponseMapConverter().convert(response);
        OAuth2AccessTokenResponse decoded = new DefaultMapOAuth2AccessTokenResponseConverter().convert(encoded);

        assertThat(encoded).containsEntry("token_type", "DPoP").containsEntry("custom", "value")
                .containsKey("expires_in");
        assertThat(decoded.getAccessToken().getTokenValue()).isEqualTo("token-7");
        assertThat(decoded.getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.DPOP);
        assertThat(decoded.getRefreshToken().getTokenValue()).isEqualTo("refresh-7");
        assertThat(decoded.getAdditionalParameters()).containsEntry("custom", "value");
    }

    @Test
    void oauth2UsersExposeAuthoritiesAttributesAndConfiguredName() {
        Map<String, Object> attributes = Map.of("id", "user-9", "name", "Ada");
        OAuth2UserAuthority authority = new OAuth2UserAuthority("ROLE_USER", attributes, "id");
        DefaultOAuth2User user = new DefaultOAuth2User(List.of(authority), attributes, "id");

        assertThat(authority.getAuthority()).isEqualTo("ROLE_USER");
        assertThat(authority.getUserNameAttributeName()).isEqualTo("id");
        assertThat(user.getName()).isEqualTo("user-9");
        assertThat(user.getAuthorities()).hasSize(1);
        assertThat(user.getAuthorities().iterator().next()).isEqualTo(authority);
        assertThat(user.getAttributes()).containsEntry("name", "Ada");
    }

    @Test
    void oidcUserCombinesIdTokenClaimsWithAuthorities() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").issuer("https://issuer.example.test")
                .subject("subject-5").audience(List.of("client-42"))
                .issuedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2025-01-01T01:00:00Z")).claim("email", "ada@example.test").build();
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

        assertThat(user.getName()).isEqualTo("subject-5");
        assertThat(user.getIdToken()).isSameAs(idToken);
        assertThat(user.getIssuer()).hasToString("https://issuer.example.test");
        assertThat(user.getEmail()).isEqualTo("ada@example.test");
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void claimConversionAndDelegatingValidatorCombineSuccessfulAndFailedResults() {
        ClaimConversionService conversionService = ClaimConversionService.getSharedInstance();
        Instant instant = conversionService.convert("2025-01-01T00:00:00Z", Instant.class);
        Boolean active = conversionService.convert("true", Boolean.class);
        OAuth2Error validationError = new OAuth2Error("invalid_token");
        OAuth2TokenValidator<OAuth2AccessToken> success = token -> OAuth2TokenValidatorResult.success();
        OAuth2TokenValidator<OAuth2AccessToken> failure = token -> OAuth2TokenValidatorResult.failure(validationError);
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", null, null);

        OAuth2TokenValidatorResult result = new DelegatingOAuth2TokenValidator<>(success, failure).validate(token);

        assertThat(instant).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(active).isTrue();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).containsExactly(validationError);
    }
}
