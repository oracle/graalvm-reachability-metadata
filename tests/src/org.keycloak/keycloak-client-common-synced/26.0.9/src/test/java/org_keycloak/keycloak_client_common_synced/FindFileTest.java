/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.FindFile;

public class FindFileTest {
    @Test
    void opensPackagedClasspathResourceWithDefiningClassLoader() throws IOException {
        String serviceResource = "META-INF/services/"
                + "org.keycloak.protocol.oidc.client.authentication.ClientCredentialsProvider";

        try (InputStream inputStream = FindFile.findFile("classpath:" + serviceResource)) {
            assertThat(readText(inputStream)).contains("ClientIdAndSecretCredentialsProvider");
        }
    }

    private static String readText(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
