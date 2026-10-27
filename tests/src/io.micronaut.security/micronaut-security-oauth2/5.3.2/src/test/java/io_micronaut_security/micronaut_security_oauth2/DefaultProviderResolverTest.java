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
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.oauth2.ProviderResolver;
import io.micronaut.security.oauth2.endpoint.token.response.OauthAuthenticationMapper;
import io.micronaut.security.token.Claims;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for resolving the OAuth provider from authentication attributes. */
@Timeout(55)
public class DefaultProviderResolverTest {
    @Test
    void resolvesExplicitProviderBeforeFallingBackToMatchingIssuer() {
        Map<String, Object> properties = Map.of(
                "micronaut.security.oauth2.clients.company.client-id", "browser-client",
                "micronaut.security.oauth2.clients.company.client-secret", "browser-secret",
                "micronaut.security.oauth2.clients.company.openid.issuer", "https://identity.example.test/");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            ProviderResolver resolver = context.getBean(ProviderResolver.class);

            Authentication explicitProvider = Authentication.build(
                    "user",
                    Map.of(
                            OauthAuthenticationMapper.PROVIDER_KEY, "partner",
                            Claims.ISSUER, "https://identity.example.test"));
            Authentication matchingIssuer =
                    Authentication.build("user", Map.of(Claims.ISSUER, "http://identity.example.test"));
            Authentication unknownIssuer =
                    Authentication.build("user", Map.of(Claims.ISSUER, "https://unknown.example.test"));

            assertThat(resolver.resolveProvider(explicitProvider)).contains("partner");
            assertThat(resolver.resolveProvider(matchingIssuer)).contains("company");
            assertThat(resolver.resolveProvider(unknownIssuer)).isEmpty();
        }
    }
}
