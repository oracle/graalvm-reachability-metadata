/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client_basic;

import com.alibaba.nacos.client.auth.ram.identify.CredentialService;
import com.alibaba.nacos.client.auth.ram.identify.Credentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class CredentialWatcherTest {
    @TempDir
    Path directory;

    @Test
    void loadsConfiguredCredentialFileAfterClasspathLookup() throws IOException {
        Path credentialFile = directory.resolve("credentials.properties");
        Files.writeString(credentialFile, """
                accessKey= example-access-key\s\s
                secretKey= example-secret-key\s\s
                tenantId= example-tenant\s\s
                """);
        String previousPath = System.setProperty("spas.identity", credentialFile.toString());
        String appName = "credential-watcher-test";
        try {
            CredentialService service = CredentialService.getInstance(appName);
            Credentials credentials = service.getCredential();

            assertTrue(credentials.valid());
            assertEquals("example-access-key", credentials.getAccessKey());
            assertEquals("example-secret-key", credentials.getSecretKey());
            assertEquals("example-tenant", credentials.getTenantId());
        } finally {
            CredentialService.freeInstance(appName);
            if (previousPath == null) {
                System.clearProperty("spas.identity");
            } else {
                System.setProperty("spas.identity", previousPath);
            }
        }
    }
}
