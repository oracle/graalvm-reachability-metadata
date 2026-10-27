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
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.oauth2.client.DefaultOpenIdProviderMetadata;
import io.micronaut.security.oauth2.configuration.OauthClientConfiguration;
import io.micronaut.security.oauth2.endpoint.endsession.request.AuthorizationServer;
import io.micronaut.security.oauth2.endpoint.endsession.request.EndSessionEndpoint;
import io.micronaut.security.oauth2.endpoint.endsession.request.EndSessionEndpointResolver;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for provider-specific OpenID end-session requests. */
@Timeout(55)
public class EndSessionEndpointResolverTest {
    @Test
    void resolvesAuth0LogoutEndpointAndBuildsReturnUrl() throws MalformedURLException {
        Map<String, Object> properties = Map.of(
                "micronaut.security.oauth2.clients.auth0.client-id", "browser-client",
                "micronaut.security.oauth2.clients.auth0.client-secret", "browser-secret",
                "micronaut.security.oauth2.clients.auth0.openid.issuer", "https://tenant.auth0.com/");

        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            OauthClientConfiguration client = context.getBeansOfType(OauthClientConfiguration.class).stream()
                    .filter(configuration -> configuration.getName().equals("auth0"))
                    .findFirst()
                    .orElseThrow();
            EndSessionEndpointResolver resolver =
                    new EndSessionEndpointResolver(context, issuer -> Optional.of(AuthorizationServer.AUTH0));
            URL callbackUrl = URI.create("https://app.example.test/signed-out").toURL();

            EndSessionEndpoint endpoint = resolver.resolve(
                            client,
                            DefaultOpenIdProviderMetadata.builder("auth0")
                                    .issuer("https://tenant.auth0.com/")
                                    .authorizationEndpoint("https://tenant.auth0.com/authorize")
                                    .build(),
                            request -> callbackUrl)
                    .orElseThrow();
            String logoutUrl = endpoint.getUrl(
                    HttpRequest.GET("https://app.example.test/logout"), Authentication.build("user", Map.of()));
            URI logoutUri = URI.create(logoutUrl);

            assertThat(logoutUri.getScheme()).isEqualTo("https");
            assertThat(logoutUri.getAuthority()).isEqualTo("tenant.auth0.com");
            assertThat(logoutUri.getPath()).isEqualTo("/v2/logout");
            assertThat(logoutUri.getQuery())
                    .contains("client_id=browser-client")
                    .contains("returnTo=https://app.example.test/signed-out");
        }
    }
}
