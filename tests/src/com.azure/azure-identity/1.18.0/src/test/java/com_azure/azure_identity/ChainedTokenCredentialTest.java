/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ChainedTokenCredential;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.CredentialUnavailableException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Mono;

@Timeout(60)
public class ChainedTokenCredentialTest {
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void fallsBackToNextCredentialWhenFirstIsUnavailable() {
        List<String> attempts = new ArrayList<>();
        TokenCredential unavailableCredential = request -> {
            attempts.add("unavailable");
            return Mono.error(new CredentialUnavailableException("Credential is not configured"));
        };
        AccessToken expectedToken = new AccessToken(
                "chained-access-token", OffsetDateTime.now().plusHours(1), OffsetDateTime.now(), "Bearer");
        TokenCredential availableCredential = request -> {
            attempts.add("available");
            return Mono.just(expectedToken);
        };
        ChainedTokenCredential credential = new ChainedTokenCredentialBuilder()
                .addFirst(unavailableCredential)
                .addLast(availableCredential)
                .build();
        TokenRequestContext requestContext = new TokenRequestContext().addScopes("https://vault.azure.net/.default");

        AccessToken token = credential.getToken(requestContext).block(ASYNC_TIMEOUT);

        assertThat(token).isSameAs(expectedToken);
        assertThat(token.getToken()).isEqualTo("chained-access-token");
        assertThat(token.getTokenType()).isEqualTo("Bearer");
        assertThat(attempts).containsExactly("unavailable", "available");
    }
}
