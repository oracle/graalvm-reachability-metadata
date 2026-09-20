/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.identity.VisualStudioCodeCredential;
import com.azure.identity.VisualStudioCodeCredentialBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class VisualStudioCodeCredentialTest {
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(10);
    private static final String AUTHENTICATION_RECORD = """
            {
              "authority": "https://login.microsoftonline.com/",
              "homeAccountId": "identity-account",
              "tenantId": "record-tenant",
              "username": "identity@example.com",
              "clientId": "identity-client"
            }
            """;

    @Test
    void createsBrokerCredentialsWithConfiguredAndRecordedTenants() throws IOException {
        Path recordPath = Path.of(
                System.getProperty("user.home"),
                ".azure",
                "ms-azuretools.vscode-azureresourcegroups",
                "authRecord.json");
        byte[] previousContents = Files.exists(recordPath) ? Files.readAllBytes(recordPath) : null;
        Files.createDirectories(recordPath.getParent());
        Files.writeString(recordPath, AUTHENTICATION_RECORD, StandardCharsets.UTF_8);

        try {
            assertInvalidRequest(new VisualStudioCodeCredentialBuilder().tenantId("configured-tenant").build());
            assertInvalidRequest(new VisualStudioCodeCredentialBuilder().build());
        } finally {
            if (previousContents == null) {
                Files.deleteIfExists(recordPath);
            } else {
                Files.write(recordPath, previousContents);
            }
        }
    }

    private static void assertInvalidRequest(VisualStudioCodeCredential credential) {
        assertThatThrownBy(() -> credential.getToken(new TokenRequestContext()).block(IO_TIMEOUT))
                .isInstanceOf(ClientAuthenticationException.class)
                .hasMessageContaining("Visual Studio Code Authentication");
    }
}
