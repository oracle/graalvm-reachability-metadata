/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_settings;

import org.apache.maven.api.settings.Server;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerTest {
    @Test
    void copiesCredentialsAndPermissionsWhenRotatingPassword() {
        Server server = Server.newBuilder().id("deployment")
                .username("publisher").password("initial-secret")
                .privateKey("deployment-key").passphrase("key-secret")
                .filePermissions("640").directoryPermissions("750").build();
        Server rotated = server.withPassword("rotated-secret");

        assertEquals("initial-secret", server.getPassword());
        assertEquals("rotated-secret", rotated.getPassword());
        assertEquals("deployment", rotated.getId());
        assertEquals("publisher", rotated.getUsername());
        assertEquals("deployment-key", rotated.getPrivateKey());
        assertEquals("key-secret", rotated.getPassphrase());
        assertEquals("640", rotated.getFilePermissions());
        assertEquals("750", rotated.getDirectoryPermissions());
    }
}
