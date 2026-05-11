/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_security_enterprise.jakarta_security_enterprise_api;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.authentication.mechanism.http.openid.DisplayType;
import jakarta.security.enterprise.authentication.mechanism.http.openid.OpenIdConstant;
import jakarta.security.enterprise.authentication.mechanism.http.openid.PromptType;
import jakarta.security.enterprise.credential.AbstractClearableCredential;
import jakarta.security.enterprise.credential.BasicAuthenticationCredential;
import jakarta.security.enterprise.credential.CallerOnlyCredential;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.Password;
import jakarta.security.enterprise.credential.RememberMeCredential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.CredentialValidationResult.Status;
import jakarta.security.enterprise.identitystore.IdentityStore;
import jakarta.security.enterprise.identitystore.IdentityStore.ValidationType;
import jakarta.security.enterprise.identitystore.IdentityStorePermission;
import jakarta.security.enterprise.identitystore.PasswordHash;
import jakarta.security.enterprise.identitystore.RememberMeIdentityStore;
import jakarta.security.enterprise.identitystore.openid.Claims;
import jakarta.security.enterprise.identitystore.openid.JwtClaims;
import jakarta.security.enterprise.identitystore.openid.OpenIdClaims;
import jakarta.security.enterprise.identitystore.openid.Scope;
import org.junit.jupiter.api.Test;

public class Jakarta_security_enterprise_apiTest {
    @Test
    void authenticationExceptionConstructorsPreserveMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("bad credentials");

        assertThat(new AuthenticationException()).isInstanceOf(GeneralSecurityException.class);
        assertThat(new AuthenticationException("denied")).hasMessage("denied").hasNoCause();
        assertThat(new AuthenticationException("denied", cause)).hasMessage("denied").hasCause(cause);
        assertThat(new AuthenticationException(cause)).hasCause(cause);
    }

    @Test
    void callerPrincipalAndAuthenticationStatusExposeExpectedValues() {
        CallerPrincipal principal = new CallerPrincipal("alice");

        assertThat(principal.getName()).isEqualTo("alice");
        assertThat(AuthenticationStatus.valueOf("SUCCESS")).isEqualTo(AuthenticationStatus.SUCCESS);
        assertThat(AuthenticationStatus.values())
                .containsExactly(
                        AuthenticationStatus.NOT_DONE,
                        AuthenticationStatus.SEND_CONTINUE,
                        AuthenticationStatus.SUCCESS,
                        AuthenticationStatus.SEND_FAILURE);
    }

    @Test
    void passwordCopiesInputComparesValuesAndClearsPreviousStorage() {
        char[] original = {'s', 'e', 'c', 'r', 'e', 't'};
        Password password = new Password(original);
        original[0] = 'X';

        char[] exposedStorage = password.getValue();
        assertThat(exposedStorage).containsExactly('s', 'e', 'c', 'r', 'e', 't');
        assertThat(password.compareTo("secret")).isTrue();
        assertThat(password.compareTo("different")).isFalse();
        assertThat(password.compareTo(null)).isFalse();

        password.clear();

        assertThat(password.getValue()).isEmpty();
        assertThat(exposedStorage).containsOnly('\0');
        assertThat(password.compareTo("secret")).isFalse();
    }

    @Test
    void passwordRejectsNullInput() {
        assertThatThrownBy(() -> new Password((char[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Password value may not be null");
        assertThatThrownBy(() -> new Password((String) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Password value may not be null");
    }

    @Test
    void usernamePasswordCredentialSupportsComparisonAndClearing() {
        UsernamePasswordCredential credential = new UsernamePasswordCredential("alice", "secret");

        assertThat(credential.getCaller()).isEqualTo("alice");
        assertThat(credential.getPasswordAsString()).isEqualTo("secret");
        assertThat(credential.compareTo("alice", "secret")).isTrue();
        assertThat(credential.compareTo("alice", "wrong")).isFalse();
        assertThat(credential.compareTo("bob", "secret")).isFalse();
        assertThat(credential.isCleared()).isFalse();
        assertThat(credential.isValid()).isTrue();

        credential.clear();

        assertThat(credential.isCleared()).isTrue();
        assertThat(credential.getPasswordAsString()).isEmpty();
        assertThat(credential.compareTo("alice", "secret")).isFalse();
    }

    @Test
    void basicAuthenticationCredentialParsesBase64UserInfo() {
        BasicAuthenticationCredential credential = new BasicAuthenticationCredential(basicUserInfo("alice:s3:cret"));

        assertThat(credential.getCaller()).isEqualTo("alice");
        assertThat(credential.getPasswordAsString()).isEqualTo("s3:cret");
        assertThat(credential.compareTo("alice", "s3:cret")).isTrue();
    }

    @Test
    void basicAuthenticationCredentialUsesEmptyPasswordWhenSeparatorIsMissing() {
        BasicAuthenticationCredential credential = new BasicAuthenticationCredential(basicUserInfo("tokenOnly"));

        assertThat(credential.getCaller()).isEqualTo("tokenOnly");
        assertThat(credential.getPasswordAsString()).isEmpty();
    }

    @Test
    void basicAuthenticationCredentialRejectsMissingHeaderValues() {
        assertThatThrownBy(() -> new BasicAuthenticationCredential(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("authorization header");
        assertThatThrownBy(() -> new BasicAuthenticationCredential(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authorization header is empty");
    }

    @Test
    void simpleCredentialTypesExposeCallerAndToken() {
        Credential defaultCredential = new Credential() {
        };
        CallerOnlyCredential callerOnlyCredential = new CallerOnlyCredential("alice");
        RememberMeCredential rememberMeCredential = new RememberMeCredential("remember-token");

        assertThat(defaultCredential.isValid()).isTrue();
        assertThat(defaultCredential.isCleared()).isFalse();
        defaultCredential.clear();
        assertThat(defaultCredential.isCleared()).isFalse();
        assertThat(callerOnlyCredential.getCaller()).isEqualTo("alice");
        assertThat(rememberMeCredential.getToken()).isEqualTo("remember-token");
    }

    @Test
    void abstractClearableCredentialInvokesCustomClearLogicAndMarksCredentialCleared() {
        RecordingClearableCredential credential = new RecordingClearableCredential("api-token");

        assertThat(credential.getToken()).isEqualTo("api-token");
        assertThat(credential.isCleared()).isFalse();
        assertThat(credential.isValid()).isTrue();

        credential.clear();

        assertThat(credential.getToken()).isEmpty();
        assertThat(credential.isCleared()).isTrue();
        assertThat(credential.isValid()).isFalse();
    }

    @Test
    void authenticationParametersSupportFluentAndBeanStyleConfiguration() {
        UsernamePasswordCredential credential = new UsernamePasswordCredential("alice", "secret");

        AuthenticationParameters parameters = AuthenticationParameters.withParams()
                .credential(credential)
                .newAuthentication(true)
                .rememberMe(true);

        assertThat(parameters.getCredential()).isSameAs(credential);
        assertThat(parameters.isNewAuthentication()).isTrue();
        assertThat(parameters.isRememberMe()).isTrue();

        parameters.setCredential(null);
        parameters.setNewAuthentication(false);
        parameters.setRememberMe(false);

        assertThat(parameters.getCredential()).isNull();
        assertThat(parameters.isNewAuthentication()).isFalse();
        assertThat(parameters.isRememberMe()).isFalse();
    }

    @Test
    void rememberMeIdentityStoreGeneratesValidatesAndRemovesLoginTokens() {
        RecordingRememberMeIdentityStore store = new RecordingRememberMeIdentityStore();

        String token = store.generateLoginToken(new CallerPrincipal("alice"), Set.of("admin", "user"));
        CredentialValidationResult validResult = store.validate(new RememberMeCredential(token));

        assertThat(token).isNotBlank();
        assertThat(validResult.getStatus()).isEqualTo(Status.VALID);
        assertThat(validResult.getCallerPrincipal().getName()).isEqualTo("alice");
        assertThat(validResult.getCallerGroups()).containsExactlyInAnyOrder("admin", "user");

        store.removeLoginToken(token);

        assertThat(store.validate(new RememberMeCredential(token))).isSameAs(CredentialValidationResult.INVALID_RESULT);
    }

    @Test
    void credentialValidationResultCreatesImmutableValidResults() {
        Set<String> suppliedGroups = new HashSet<>(Arrays.asList("admin", "user"));
        CredentialValidationResult result = new CredentialValidationResult(
                "store", new CallerPrincipal("alice"), "uid=alice", "42", suppliedGroups);
        suppliedGroups.add("mutated");

        assertThat(result.getStatus()).isEqualTo(Status.VALID);
        assertThat(result.getIdentityStoreId()).isEqualTo("store");
        assertThat(result.getCallerPrincipal().getName()).isEqualTo("alice");
        assertThat(result.getCallerDn()).isEqualTo("uid=alice");
        assertThat(result.getCallerUniqueId()).isEqualTo("42");
        assertThat(result.getCallerGroups()).containsExactlyInAnyOrder("admin", "user");
        assertThatThrownBy(() -> result.getCallerGroups().add("auditor"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void credentialValidationResultValidatesCallerPrincipalAndStatusConstants() {
        CredentialValidationResult withNameOnly = new CredentialValidationResult("alice");
        CredentialValidationResult withNullGroups = new CredentialValidationResult(new CallerPrincipal("bob"), null);

        assertThat(withNameOnly.getStatus()).isEqualTo(Status.VALID);
        assertThat(withNameOnly.getCallerPrincipal().getName()).isEqualTo("alice");
        assertThat(withNameOnly.getCallerGroups()).isEmpty();
        assertThat(withNullGroups.getStatus()).isEqualTo(Status.VALID);
        assertThat(withNullGroups.getCallerGroups()).isEmpty();
        assertThat(CredentialValidationResult.INVALID_RESULT.getStatus()).isEqualTo(Status.INVALID);
        assertThat(CredentialValidationResult.NOT_VALIDATED_RESULT.getStatus()).isEqualTo(Status.NOT_VALIDATED);
        assertThat(CredentialValidationResult.INVALID_RESULT.getCallerPrincipal()).isNull();

        assertThatThrownBy(() -> new CredentialValidationResult("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null or empty CallerPrincipal");
    }

    @Test
    void identityStoreDefaultsAndTypedValidationDispatchWork() {
        IdentityStore defaultStore = new DefaultIdentityStore();
        UsernamePasswordCredential credential = new UsernamePasswordCredential("alice", "secret");
        TypedIdentityStore typedStore = new TypedIdentityStore();

        assertThat(defaultStore.validate(credential)).isSameAs(CredentialValidationResult.NOT_VALIDATED_RESULT);
        assertThat(defaultStore.getCallerGroups(new CredentialValidationResult("alice"))).isEmpty();
        assertThat(defaultStore.priority()).isEqualTo(100);
        assertThat(defaultStore.validationTypes())
                .containsExactlyInAnyOrder(ValidationType.VALIDATE, ValidationType.PROVIDE_GROUPS)
                .isSameAs(IdentityStore.DEFAULT_VALIDATION_TYPES);

        CredentialValidationResult result = typedStore.validate(credential);

        assertThat(result.getStatus()).isEqualTo(Status.VALID);
        assertThat(result.getCallerPrincipal().getName()).isEqualTo("alice");
        assertThat(result.getCallerGroups()).containsExactly("validated");
    }

    @Test
    void identityStorePermissionUsesBasicPermissionSemantics() {
        IdentityStorePermission wildcard = new IdentityStorePermission("*");
        IdentityStorePermission validate = new IdentityStorePermission("validate", "ignored-actions");

        assertThat(wildcard.implies(validate)).isTrue();
        assertThat(validate.getName()).isEqualTo("validate");
        assertThat(validate.getActions()).isEmpty();
    }

    @Test
    void passwordHashDefaultInitializeIsNoOpForImplementations() {
        RecordingPasswordHash passwordHash = new RecordingPasswordHash();

        passwordHash.initialize(Collections.singletonMap("ignored", "true"));
        String generated = passwordHash.generate("secret".toCharArray());

        assertThat(generated).isEqualTo("hashed:secret");
        assertThat(passwordHash.verify("secret".toCharArray(), generated)).isTrue();
        assertThat(passwordHash.verify("wrong".toCharArray(), generated)).isFalse();
    }

    @Test
    void scopeParsesSpaceDelimitedValuesAndKeepsInsertionOrder() {
        Scope parsed = Scope.parse("openid email profile email");
        Scope fromList = new Scope(Arrays.asList("openid", "email"));

        assertThat(Scope.parse(null)).isNull();
        assertThat(Scope.parse("   ")).isEmpty();
        assertThat(parsed).containsExactly("openid", "email", "profile");
        assertThat(parsed.toString()).isEqualTo("openid email profile");
        assertThat(fromList.toString()).isEqualTo("openid email");
    }

    @Test
    void openIdEnumFactoriesAcceptCaseInsensitiveNamesAndBlankValues() {
        assertThat(DisplayType.fromString("popup")).isEqualTo(DisplayType.POPUP);
        assertThat(DisplayType.fromString("TOUCH")).isEqualTo(DisplayType.TOUCH);
        assertThat(DisplayType.fromString(null)).isNull();
        assertThat(DisplayType.fromString(" ")).isNull();
        assertThat(PromptType.fromString("login")).isEqualTo(PromptType.LOGIN);
        assertThat(PromptType.fromString("SELECT_ACCOUNT")).isEqualTo(PromptType.SELECT_ACCOUNT);
        assertThat(PromptType.fromString(null)).isNull();
        assertThat(PromptType.fromString(" ")).isNull();
    }

    @Test
    void openIdConstantsExposeStandardParameterNamesAndFlowTypes() {
        assertThat(OpenIdConstant.RESPONSE_TYPE).isEqualTo("response_type");
        assertThat(OpenIdConstant.CLIENT_ID).isEqualTo("client_id");
        assertThat(OpenIdConstant.OPENID_SCOPE).isEqualTo("openid");
        assertThat(OpenIdConstant.EMAIL).isEqualTo("email");
        assertThat(OpenIdConstant.AUTHORIZATION_CODE_FLOW_TYPES).containsExactly("code");
        assertThat(OpenIdConstant.IMPLICIT_FLOW_TYPES).containsExactlyInAnyOrder("id_token", "id_token token");
        assertThat(OpenIdConstant.HYBRID_FLOW_TYPES)
                .containsExactlyInAnyOrder("code id_token", "code token", "code id_token token");
    }

    @Test
    void jwtClaimsDefaultMethodsReadRegisteredClaimNamesAndEvaluateValidityWindows() {
        Instant now = Instant.ofEpochSecond(Instant.now().getEpochSecond());
        MapJwtClaims claims = new MapJwtClaims(Map.<String, Object>of(
                "iss", "https://issuer.example",
                "sub", "alice",
                "aud", List.of("client-a", "client-b"),
                "exp", now.plusSeconds(120).getEpochSecond(),
                "nbf", now.minusSeconds(5).getEpochSecond(),
                "iat", now.minusSeconds(30).getEpochSecond(),
                "jti", "jwt-1"));
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        assertThat(claims.getIssuer()).contains("https://issuer.example");
        assertThat(claims.getSubject()).contains("alice");
        assertThat(claims.getAudience()).containsExactly("client-a", "client-b");
        assertThat(claims.getExpirationTime()).contains(now.plusSeconds(120));
        assertThat(claims.getIssuedAt()).contains(now.minusSeconds(30));
        assertThat(claims.getJwtId()).contains("jwt-1");
        assertThat(claims.isExpired(clock, true, Duration.ofSeconds(10))).isFalse();
        assertThat(claims.isBeforeValidity(clock, false, Duration.ofSeconds(10))).isFalse();
        assertThat(claims.isValid()).isTrue();
    }

    @Test
    void jwtClaimsValidityDefaultsApplyWhenDateClaimsAreMissing() {
        JwtClaims emptyClaims = JwtClaims.NONE;
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC);

        assertThat(emptyClaims.getIssuer()).isEmpty();
        assertThat(emptyClaims.getAudience()).isEmpty();
        assertThat(emptyClaims.isExpired(clock, true, Duration.ZERO)).isTrue();
        assertThat(emptyClaims.isExpired(clock, false, Duration.ZERO)).isFalse();
        assertThat(emptyClaims.isBeforeValidity(clock, true, Duration.ZERO)).isTrue();
        assertThat(emptyClaims.isBeforeValidity(clock, false, Duration.ZERO)).isFalse();
        assertThat(emptyClaims.isValid()).isFalse();
    }

    @Test
    void openIdClaimsDefaultMethodsReadUserInfoClaimNames() {
        MapOpenIdClaims claims = new MapOpenIdClaims(Map.<String, Object>ofEntries(
                Map.entry("sub", "alice-subject"),
                Map.entry("name", "Alice A."),
                Map.entry("family_name", "Anderson"),
                Map.entry("given_name", "Alice"),
                Map.entry("middle_name", "Beth"),
                Map.entry("nickname", "ally"),
                Map.entry("preferred_username", "alice"),
                Map.entry("profile", "https://example.test/alice"),
                Map.entry("picture", "https://example.test/alice.png"),
                Map.entry("website", "https://alice.example"),
                Map.entry("gender", "female"),
                Map.entry("birthdate", "2000-01-01"),
                Map.entry("zoneinfo", "UTC"),
                Map.entry("locale", "en-US"),
                Map.entry("updated_at", "1700000000"),
                Map.entry("email", "alice@example.test"),
                Map.entry("email_verified", "true"),
                Map.entry("address", "main street"),
                Map.entry("phone_number", "+123"),
                Map.entry("phone_number_verified", "false")));

        assertThat(claims.getSubject()).isEqualTo("alice-subject");
        assertThat(claims.getName()).contains("Alice A.");
        assertThat(claims.getFamilyName()).contains("Anderson");
        assertThat(claims.getGivenName()).contains("Alice");
        assertThat(claims.getMiddleName()).contains("Beth");
        assertThat(claims.getNickname()).contains("ally");
        assertThat(claims.getPreferredUsername()).contains("alice");
        assertThat(claims.getProfile()).contains("https://example.test/alice");
        assertThat(claims.getPicture()).contains("https://example.test/alice.png");
        assertThat(claims.getWebsite()).contains("https://alice.example");
        assertThat(claims.getGender()).contains("female");
        assertThat(claims.getBirthdate()).contains("2000-01-01");
        assertThat(claims.getZoneinfo()).contains("UTC");
        assertThat(claims.getLocale()).contains("en-US");
        assertThat(claims.getUpdatedAt()).contains("1700000000");
        assertThat(claims.getEmail()).contains("alice@example.test");
        assertThat(claims.getEmailVerified()).contains("true");
        assertThat(claims.getAddress()).contains("main street");
        assertThat(claims.getPhoneNumber()).contains("+123");
        assertThat(claims.getPhoneNumberVerified()).contains("false");
    }

    @Test
    void openIdClaimsRequireSubjectForUserInfoResponse() {
        OpenIdClaims claims = new MapOpenIdClaims(Collections.emptyMap());

        assertThatThrownBy(claims::getSubject)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payload does not represent correct UserInfo response");
    }

    private static String basicUserInfo(String userInfo) {
        return Base64.getEncoder().encodeToString(userInfo.getBytes(US_ASCII));
    }

    public static final class RecordingClearableCredential extends AbstractClearableCredential {
        private String token;

        private RecordingClearableCredential(String token) {
            this.token = token;
        }

        private String getToken() {
            return token;
        }

        @Override
        public boolean isValid() {
            return !isCleared() && !token.isEmpty();
        }

        @Override
        protected void clearCredential() {
            token = "";
        }
    }

    public static final class RecordingRememberMeIdentityStore implements RememberMeIdentityStore {
        private final Map<String, CredentialValidationResult> tokens = new LinkedHashMap<>();
        private int nextTokenId;

        @Override
        public CredentialValidationResult validate(RememberMeCredential credential) {
            return tokens.getOrDefault(credential.getToken(), CredentialValidationResult.INVALID_RESULT);
        }

        @Override
        public String generateLoginToken(CallerPrincipal callerPrincipal, Set<String> groups) {
            String token = "remember-token-" + nextTokenId++;
            tokens.put(token, new CredentialValidationResult(callerPrincipal, groups));
            return token;
        }

        @Override
        public void removeLoginToken(String token) {
            tokens.remove(token);
        }
    }

    public static final class DefaultIdentityStore implements IdentityStore {
    }

    public static final class TypedIdentityStore implements IdentityStore {
        public CredentialValidationResult validate(UsernamePasswordCredential credential) {
            return new CredentialValidationResult(credential.getCaller(), Set.of("validated"));
        }
    }

    private static final class RecordingPasswordHash implements PasswordHash {
        @Override
        public String generate(char[] password) {
            return "hashed:" + String.valueOf(password);
        }

        @Override
        public boolean verify(char[] password, String hashedPassword) {
            return generate(password).equals(hashedPassword);
        }
    }

    private static class BaseClaims implements Claims {
        private final Map<String, Object> claims;

        private BaseClaims(Map<String, Object> claims) {
            this.claims = new LinkedHashMap<>(claims);
        }

        @Override
        public Optional<String> getStringClaim(String name) {
            Object value = claims.get(name);
            if (value instanceof String stringValue) {
                return Optional.of(stringValue);
            }
            return Optional.empty();
        }

        @Override
        public Optional<Instant> getNumericDateClaim(String name) {
            Object value = claims.get(name);
            if (value instanceof Number numericValue) {
                return Optional.of(Instant.ofEpochSecond(numericValue.longValue()));
            }
            return Optional.empty();
        }

        @Override
        public List<String> getArrayStringClaim(String name) {
            Object value = claims.get(name);
            if (value instanceof List<?> listValue) {
                return listValue.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            }
            if (value instanceof String stringValue) {
                return List.of(stringValue);
            }
            return Collections.emptyList();
        }

        @Override
        public OptionalInt getIntClaim(String name) {
            Object value = claims.get(name);
            if (value instanceof Number numericValue) {
                return OptionalInt.of(numericValue.intValue());
            }
            return OptionalInt.empty();
        }

        @Override
        public OptionalLong getLongClaim(String name) {
            Object value = claims.get(name);
            if (value instanceof Number numericValue) {
                return OptionalLong.of(numericValue.longValue());
            }
            return OptionalLong.empty();
        }

        @Override
        public OptionalDouble getDoubleClaim(String name) {
            Object value = claims.get(name);
            if (value instanceof Number numericValue) {
                return OptionalDouble.of(numericValue.doubleValue());
            }
            return OptionalDouble.empty();
        }

        @Override
        public Optional<Claims> getNested(String name) {
            Object value = claims.get(name);
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> nestedClaims = new LinkedHashMap<>();
                mapValue.forEach((key, nestedValue) -> {
                    if (key instanceof String stringKey) {
                        nestedClaims.put(stringKey, nestedValue);
                    }
                });
                return Optional.of(new BaseClaims(nestedClaims));
            }
            return Optional.empty();
        }
    }

    private static final class MapJwtClaims extends BaseClaims implements JwtClaims {
        private MapJwtClaims(Map<String, Object> claims) {
            super(claims);
        }
    }

    private static final class MapOpenIdClaims extends BaseClaims implements OpenIdClaims {
        private MapOpenIdClaims(Map<String, Object> claims) {
            super(claims);
        }
    }
}
