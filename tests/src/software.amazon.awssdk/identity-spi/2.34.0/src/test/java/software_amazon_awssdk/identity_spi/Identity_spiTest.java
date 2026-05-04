/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.identity_spi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.identity.spi.Identity;
import software.amazon.awssdk.identity.spi.IdentityProperty;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.identity.spi.IdentityProviders;
import software.amazon.awssdk.identity.spi.ResolveIdentityRequest;
import software.amazon.awssdk.identity.spi.TokenIdentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Identity_spiTest {
    private static final IdentityProperty<String> ACCOUNT_ID_PROPERTY =
            IdentityProperty.create(Identity_spiTest.class, "accountId");
    private static final IdentityProperty<String> REGION_PROPERTY =
            IdentityProperty.create(Identity_spiTest.class, "region");
    private static final IdentityProperty<Integer> ATTEMPT_PROPERTY =
            IdentityProperty.create(Identity_spiTest.class, "attempt");
    private static final IdentityProperty<String> AUDIENCE_PROPERTY =
            IdentityProperty.create(Identity_spiTest.class, "audience");
    private static final IdentityProperty<String> SUBJECT_PROPERTY =
            IdentityProperty.create(CustomIdentity.class, "subject");
    private static final IdentityProperty<String> PRIMARY_SHARED_NAME_PROPERTY =
            IdentityProperty.create(PrimaryPropertyNamespace.class, "sharedName");
    private static final IdentityProperty<String> SECONDARY_SHARED_NAME_PROPERTY =
            IdentityProperty.create(SecondaryPropertyNamespace.class, "sharedName");

    @Test
    void awsCredentialsFactoryAndBuilderExposeRequiredAndOptionalFields() {
        AwsCredentialsIdentity factoryIdentity = AwsCredentialsIdentity.create("access-key", "secret-key");
        AwsCredentialsIdentity builderIdentity = AwsCredentialsIdentity.builder()
                .accessKeyId("access-key")
                .secretAccessKey("secret-key")
                .accountId("123456789012")
                .providerName("test-credentials-provider")
                .build();

        assertThat(factoryIdentity.accessKeyId()).isEqualTo("access-key");
        assertThat(factoryIdentity.secretAccessKey()).isEqualTo("secret-key");
        assertThat(factoryIdentity.accountId()).isEmpty();
        assertThat(factoryIdentity.providerName()).isEmpty();
        assertThat(factoryIdentity.expirationTime()).isEmpty();
        assertThat(factoryIdentity.toString()).contains("access-key").doesNotContain("secret-key");

        assertThat(builderIdentity.accessKeyId()).isEqualTo("access-key");
        assertThat(builderIdentity.secretAccessKey()).isEqualTo("secret-key");
        assertThat(builderIdentity.accountId()).contains("123456789012");
        assertThat(builderIdentity.providerName()).contains("test-credentials-provider");
        assertThat(builderIdentity.expirationTime()).isEmpty();
        assertThat(builderIdentity.toString())
                .contains("AwsCredentialsIdentity", "access-key", "test-credentials-provider", "123456789012")
                .doesNotContain("secret-key");
    }

    @Test
    void awsCredentialsEqualityUsesCredentialMaterialAndAccountIdButNotProviderName() {
        AwsCredentialsIdentity first = AwsCredentialsIdentity.builder()
                .accessKeyId("access-key")
                .secretAccessKey("secret-key")
                .accountId("123456789012")
                .providerName("first-provider")
                .build();
        AwsCredentialsIdentity sameCredentialDifferentProvider = AwsCredentialsIdentity.builder()
                .accessKeyId("access-key")
                .secretAccessKey("secret-key")
                .accountId("123456789012")
                .providerName("second-provider")
                .build();
        AwsCredentialsIdentity differentAccount = AwsCredentialsIdentity.builder()
                .accessKeyId("access-key")
                .secretAccessKey("secret-key")
                .accountId("999999999999")
                .build();

        assertThat(first)
                .isEqualTo(sameCredentialDifferentProvider)
                .hasSameHashCodeAs(sameCredentialDifferentProvider)
                .isNotEqualTo(differentAccount)
                .isNotEqualTo("access-key");
    }

    @Test
    void awsCredentialsBuilderRequiresAccessKeyAndSecretKey() {
        assertThatThrownBy(() -> AwsCredentialsIdentity.create(null, "secret-key"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AwsCredentialsIdentity.create("access-key", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AwsCredentialsIdentity.builder()
                .secretAccessKey("secret-key")
                .build()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AwsCredentialsIdentity.builder()
                .accessKeyId("access-key")
                .build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void awsSessionCredentialsFactoryAndBuilderExposeSessionSpecificFields() {
        AwsSessionCredentialsIdentity factoryIdentity =
                AwsSessionCredentialsIdentity.create("session-access", "session-secret", "session-token");
        AwsSessionCredentialsIdentity builderIdentity = AwsSessionCredentialsIdentity.builder()
                .accessKeyId("session-access")
                .secretAccessKey("session-secret")
                .sessionToken("session-token")
                .accountId("123456789012")
                .providerName("test-session-provider")
                .build();

        assertThat(factoryIdentity.accessKeyId()).isEqualTo("session-access");
        assertThat(factoryIdentity.secretAccessKey()).isEqualTo("session-secret");
        assertThat(factoryIdentity.sessionToken()).isEqualTo("session-token");
        assertThat(factoryIdentity.accountId()).isEmpty();
        assertThat(factoryIdentity.providerName()).isEmpty();
        assertThat(factoryIdentity.expirationTime()).isEmpty();
        assertThat(factoryIdentity.toString()).doesNotContain("session-secret", "session-token");

        assertThat(builderIdentity.accessKeyId()).isEqualTo("session-access");
        assertThat(builderIdentity.secretAccessKey()).isEqualTo("session-secret");
        assertThat(builderIdentity.sessionToken()).isEqualTo("session-token");
        assertThat(builderIdentity.accountId()).contains("123456789012");
        assertThat(builderIdentity.providerName()).contains("test-session-provider");
        assertThat(builderIdentity.toString())
                .contains("AwsSessionCredentialsIdentity", "session-access", "test-session-provider", "123456789012")
                .doesNotContain("session-secret", "session-token");
    }

    @Test
    void awsSessionCredentialsEqualityUsesSessionTokenAndAccountIdButNotProviderName() {
        AwsSessionCredentialsIdentity first = AwsSessionCredentialsIdentity.builder()
                .accessKeyId("session-access")
                .secretAccessKey("session-secret")
                .sessionToken("session-token")
                .accountId("123456789012")
                .providerName("first-provider")
                .build();
        AwsSessionCredentialsIdentity sameCredentialDifferentProvider = AwsSessionCredentialsIdentity.builder()
                .accessKeyId("session-access")
                .secretAccessKey("session-secret")
                .sessionToken("session-token")
                .accountId("123456789012")
                .providerName("second-provider")
                .build();
        AwsSessionCredentialsIdentity differentToken = AwsSessionCredentialsIdentity.builder()
                .accessKeyId("session-access")
                .secretAccessKey("session-secret")
                .sessionToken("different-token")
                .accountId("123456789012")
                .build();

        assertThat(first)
                .isEqualTo(sameCredentialDifferentProvider)
                .hasSameHashCodeAs(sameCredentialDifferentProvider)
                .isNotEqualTo(differentToken)
                .isNotEqualTo(AwsCredentialsIdentity.create("session-access", "session-secret"));
    }

    @Test
    void awsSessionCredentialsBuilderRequiresAccessKeySecretKeyAndSessionToken() {
        assertThatThrownBy(() -> AwsSessionCredentialsIdentity.create(null, "secret-key", "session-token"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AwsSessionCredentialsIdentity.create("access-key", null, "session-token"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AwsSessionCredentialsIdentity.create("access-key", "secret-key", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AwsSessionCredentialsIdentity.builder()
                .accessKeyId("access-key")
                .secretAccessKey("secret-key")
                .build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void tokenIdentityFactoryCreatesValueObjectWithDefaultIdentityOptions() {
        TokenIdentity token = TokenIdentity.create("bearer-token");
        TokenIdentity sameToken = TokenIdentity.create("bearer-token");
        TokenIdentity differentToken = TokenIdentity.create("other-token");

        assertThat(token.token()).isEqualTo("bearer-token");
        assertThat(token.providerName()).isEmpty();
        assertThat(token.expirationTime()).isEmpty();
        assertThat(token)
                .isEqualTo(sameToken)
                .hasSameHashCodeAs(sameToken)
                .isNotEqualTo(differentToken)
                .isNotEqualTo("bearer-token");
        assertThat(token.toString()).contains("TokenIdentity", "bearer-token");
        assertThatThrownBy(() -> TokenIdentity.create(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveIdentityRequestStoresTypedPropertiesAndCopiesIndependently() {
        ResolveIdentityRequest.Builder builder = ResolveIdentityRequest.builder()
                .putProperty(ACCOUNT_ID_PROPERTY, "123456789012")
                .putProperty(REGION_PROPERTY, "us-east-1")
                .putProperty(ATTEMPT_PROPERTY, 3);
        ResolveIdentityRequest original = builder.build();
        builder.putProperty(REGION_PROPERTY, "eu-west-1");
        ResolveIdentityRequest copied = original.toBuilder()
                .putProperty(REGION_PROPERTY, "ap-south-1")
                .putProperty(AUDIENCE_PROPERTY, "sts.amazonaws.com")
                .build();

        assertThat(original.property(ACCOUNT_ID_PROPERTY)).isEqualTo("123456789012");
        assertThat(original.property(REGION_PROPERTY)).isEqualTo("us-east-1");
        assertThat(original.property(ATTEMPT_PROPERTY)).isEqualTo(3);
        assertThat(original.property(AUDIENCE_PROPERTY)).isNull();
        assertThat(copied.property(ACCOUNT_ID_PROPERTY)).isEqualTo("123456789012");
        assertThat(copied.property(REGION_PROPERTY)).isEqualTo("ap-south-1");
        assertThat(copied.property(AUDIENCE_PROPERTY)).isEqualTo("sts.amazonaws.com");
        assertThat(copied.toString()).contains("ResolveIdentityRequest", "ap-south-1", "sts.amazonaws.com");
    }

    @Test
    void resolveIdentityRequestSupportsValueSemanticsForEquivalentPropertyMaps() {
        ResolveIdentityRequest first = ResolveIdentityRequest.builder()
                .putProperty(REGION_PROPERTY, "us-east-1")
                .putProperty(ATTEMPT_PROPERTY, 1)
                .build();
        ResolveIdentityRequest equivalent = ResolveIdentityRequest.builder()
                .putProperty(ATTEMPT_PROPERTY, 1)
                .putProperty(REGION_PROPERTY, "us-east-1")
                .build();
        ResolveIdentityRequest different = ResolveIdentityRequest.builder()
                .putProperty(REGION_PROPERTY, "us-west-2")
                .putProperty(ATTEMPT_PROPERTY, 1)
                .build();

        assertThat(first)
                .isEqualTo(equivalent)
                .hasSameHashCodeAs(equivalent)
                .isNotEqualTo(different)
                .isNotEqualTo("us-east-1");
    }

    @Test
    void identityPropertyNamesAreUniqueWithinANamespace() {
        IdentityProperty<String> first = IdentityProperty.create(UniquePropertyNamespace.class, "uniqueName");
        IdentityProperty<String> differentName = IdentityProperty.create(
                UniquePropertyNamespace.class, "otherUniqueName");

        assertThat(first)
                .isEqualTo(first)
                .isNotEqualTo(differentName)
                .isNotEqualTo("uniqueName");
        assertThat(first.toString()).contains("UniquePropertyNamespace", "uniqueName");
        assertThatThrownBy(() -> IdentityProperty.create(UniquePropertyNamespace.class, "uniqueName"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identityPropertiesWithSameNameInDifferentNamespacesDoNotCollide() {
        ResolveIdentityRequest request = ResolveIdentityRequest.builder()
                .putProperty(PRIMARY_SHARED_NAME_PROPERTY, "primary-value")
                .putProperty(SECONDARY_SHARED_NAME_PROPERTY, "secondary-value")
                .build();

        assertThat(PRIMARY_SHARED_NAME_PROPERTY).isNotEqualTo(SECONDARY_SHARED_NAME_PROPERTY);
        assertThat(PRIMARY_SHARED_NAME_PROPERTY.toString()).contains("PrimaryPropertyNamespace", "sharedName");
        assertThat(SECONDARY_SHARED_NAME_PROPERTY.toString()).contains("SecondaryPropertyNamespace", "sharedName");
        assertThat(request.property(PRIMARY_SHARED_NAME_PROPERTY)).isEqualTo("primary-value");
        assertThat(request.property(SECONDARY_SHARED_NAME_PROPERTY)).isEqualTo("secondary-value");
    }

    @Test
    void identityProvidersRegistrySupportsApplicationDefinedIdentityTypes() throws Exception {
        CustomIdentityProvider provider = new CustomIdentityProvider();
        ResolveIdentityRequest request = ResolveIdentityRequest.builder()
                .putProperty(SUBJECT_PROPERTY, "user-123")
                .putProperty(AUDIENCE_PROPERTY, "internal-service")
                .build();
        IdentityProviders providers = IdentityProviders.builder()
                .putIdentityProvider(provider)
                .build();

        IdentityProvider<CustomIdentity> registeredProvider = providers.identityProvider(CustomIdentity.class);
        CustomIdentity identity = registeredProvider.resolveIdentity(request).get(1, TimeUnit.SECONDS);

        assertThat(registeredProvider).isSameAs(provider);
        assertThat(identity.subject()).isEqualTo("user-123");
        assertThat(identity.audience()).isEqualTo("internal-service");
        assertThat(identity.providerName()).isEmpty();
        assertThat(identity.expirationTime()).isEmpty();
    }

    @Test
    void identityProviderDefaultResolveMethodsBuildRequestsBeforeDelegating() throws Exception {
        RequestAwareCredentialsProvider provider = new RequestAwareCredentialsProvider();

        AwsCredentialsIdentity defaultIdentity = provider.resolveIdentity().get(1, TimeUnit.SECONDS);
        AwsCredentialsIdentity requestedIdentity = provider.resolveIdentity(builder -> builder
                .putProperty(ACCOUNT_ID_PROPERTY, "123456789012")
                .putProperty(REGION_PROPERTY, "eu-central-1"))
                .get(1, TimeUnit.SECONDS);

        assertThat(provider.identityType()).isEqualTo(AwsCredentialsIdentity.class);
        assertThat(defaultIdentity.accessKeyId()).isEqualTo("access-default-account");
        assertThat(defaultIdentity.accountId()).contains("default-account");
        assertThat(requestedIdentity.accessKeyId()).isEqualTo("access-123456789012");
        assertThat(requestedIdentity.accountId()).contains("123456789012");
        assertThat(requestedIdentity.providerName()).contains("request-aware-provider");
        assertThat(provider.lastRegion()).isEqualTo("eu-central-1");
    }

    @Test
    void identityProvidersRegistryResolvesProvidersByIdentityTypeAndCanBeCopied() {
        RequestAwareCredentialsProvider credentialsProvider = new RequestAwareCredentialsProvider();
        StaticTokenProvider firstTokenProvider = new StaticTokenProvider("first-token");
        StaticTokenProvider replacementTokenProvider = new StaticTokenProvider("replacement-token");
        StaticSessionCredentialsProvider sessionProvider = new StaticSessionCredentialsProvider();

        IdentityProviders original = IdentityProviders.builder()
                .putIdentityProvider(credentialsProvider)
                .putIdentityProvider(firstTokenProvider)
                .putIdentityProvider(replacementTokenProvider)
                .build();
        IdentityProviders copied = original.toBuilder()
                .putIdentityProvider(sessionProvider)
                .build();

        assertThat(original.identityProvider(AwsCredentialsIdentity.class)).isSameAs(credentialsProvider);
        assertThat(original.identityProvider(TokenIdentity.class)).isSameAs(replacementTokenProvider);
        assertThat(original.identityProvider(AwsSessionCredentialsIdentity.class)).isNull();
        assertThat(original.toString()).contains("IdentityProviders", "replacement-token");
        assertThat(copied.identityProvider(AwsCredentialsIdentity.class)).isSameAs(credentialsProvider);
        assertThat(copied.identityProvider(TokenIdentity.class)).isSameAs(replacementTokenProvider);
        assertThat(copied.identityProvider(AwsSessionCredentialsIdentity.class)).isSameAs(sessionProvider);
        assertThatThrownBy(() -> IdentityProviders.builder().putIdentityProvider(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static final class RequestAwareCredentialsProvider implements IdentityProvider<AwsCredentialsIdentity> {
        private String lastRegion;

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(ResolveIdentityRequest request) {
            String accountId = Objects.requireNonNullElse(request.property(ACCOUNT_ID_PROPERTY), "default-account");
            lastRegion = request.property(REGION_PROPERTY);
            AwsCredentialsIdentity identity = AwsCredentialsIdentity.builder()
                    .accessKeyId("access-" + accountId)
                    .secretAccessKey("secret-" + accountId)
                    .accountId(accountId)
                    .providerName("request-aware-provider")
                    .build();
            return CompletableFuture.completedFuture(identity);
        }

        private String lastRegion() {
            return lastRegion;
        }
    }

    private static final class CustomIdentity implements Identity {
        private final String subject;
        private final String audience;

        private CustomIdentity(String subject, String audience) {
            this.subject = subject;
            this.audience = audience;
        }

        private String subject() {
            return subject;
        }

        private String audience() {
            return audience;
        }
    }

    private static final class CustomIdentityProvider implements IdentityProvider<CustomIdentity> {
        @Override
        public Class<CustomIdentity> identityType() {
            return CustomIdentity.class;
        }

        @Override
        public CompletableFuture<CustomIdentity> resolveIdentity(ResolveIdentityRequest request) {
            String subject = Objects.requireNonNull(request.property(SUBJECT_PROPERTY));
            String audience = Objects.requireNonNull(request.property(AUDIENCE_PROPERTY));
            return CompletableFuture.completedFuture(new CustomIdentity(subject, audience));
        }
    }

    private static final class StaticTokenProvider implements IdentityProvider<TokenIdentity> {
        private final String token;

        private StaticTokenProvider(String token) {
            this.token = token;
        }

        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }

        @Override
        public CompletableFuture<TokenIdentity> resolveIdentity(ResolveIdentityRequest request) {
            return CompletableFuture.completedFuture(TokenIdentity.create(token));
        }

        @Override
        public String toString() {
            return "StaticTokenProvider{" + token + '}';
        }
    }

    private static final class StaticSessionCredentialsProvider
            implements IdentityProvider<AwsSessionCredentialsIdentity> {
        @Override
        public Class<AwsSessionCredentialsIdentity> identityType() {
            return AwsSessionCredentialsIdentity.class;
        }

        @Override
        public CompletableFuture<AwsSessionCredentialsIdentity> resolveIdentity(ResolveIdentityRequest request) {
            return CompletableFuture.completedFuture(AwsSessionCredentialsIdentity.builder()
                    .accessKeyId("session-access")
                    .secretAccessKey("session-secret")
                    .sessionToken("session-token")
                    .build());
        }
    }

    private static final class UniquePropertyNamespace {
    }

    private static final class PrimaryPropertyNamespace {
    }

    private static final class SecondaryPropertyNamespace {
    }
}
