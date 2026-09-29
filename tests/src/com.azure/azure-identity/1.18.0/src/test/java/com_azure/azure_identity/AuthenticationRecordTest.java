/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_identity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.identity.AuthenticationRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class AuthenticationRecordTest {
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);
    private static final String AUTHENTICATION_RECORD = """
            {
              "authority": "login.microsoftonline.com",
              "homeAccountId": "home-account-id",
              "tenantId": "tenant-id",
              "username": "identity-user@example.test",
              "clientId": "client-id",
              "extension": {
                "ignored": true
              }
            }
            """;

    @Test
    void roundTripsAuthenticationRecordThroughStreams() {
        AuthenticationRecord record = AuthenticationRecord.deserializeAsync(
                        new ByteArrayInputStream(AUTHENTICATION_RECORD.getBytes(UTF_8)))
                .block(ASYNC_TIMEOUT);

        assertRecord(record);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStream serialized = record.serializeAsync(output).block(ASYNC_TIMEOUT);

        assertThat(serialized).isSameAs(output);
        AuthenticationRecord restored =
                AuthenticationRecord.deserialize(new ByteArrayInputStream(output.toByteArray()));
        assertRecord(restored);
    }

    private static void assertRecord(AuthenticationRecord record) {
        assertThat(record).isNotNull();
        assertThat(record.getAuthority()).isEqualTo("login.microsoftonline.com");
        assertThat(record.getHomeAccountId()).isEqualTo("home-account-id");
        assertThat(record.getTenantId()).isEqualTo("tenant-id");
        assertThat(record.getUsername()).isEqualTo("identity-user@example.test");
        assertThat(record.getClientId()).isEqualTo("client-id");
    }
}
