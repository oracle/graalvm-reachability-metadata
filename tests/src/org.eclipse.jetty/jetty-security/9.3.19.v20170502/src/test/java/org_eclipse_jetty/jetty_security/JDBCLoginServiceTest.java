/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jetty.security.JDBCLoginService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JDBCLoginServiceTest {
    @Test
    void startLoadsConfiguredDriverAndConnectDatabaseLoadsItAgain(@TempDir Path tempDir) throws Exception {
        JDBCLoginServiceDriver.reset();

        Path config = tempDir.resolve("jdbc-login-service.properties");
        Files.write(config, jdbcLoginServiceProperties().getBytes(StandardCharsets.UTF_8));

        JDBCLoginService service = new JDBCLoginService("jdbc-realm", config.toUri().toString());
        try {
            service.start();
            assertThat(service.isStarted()).isTrue();
            assertThat(JDBCLoginServiceDriver.instantiations()).isEqualTo(1);

            service.connectDatabase();

            assertThat(JDBCLoginServiceDriver.connectAttempts()).isGreaterThanOrEqualTo(1);
        } finally {
            service.stop();
            JDBCLoginServiceDriver.deregisterRegisteredDrivers();
        }
    }

    private static String jdbcLoginServiceProperties() {
        return """
                jdbcdriver=org_eclipse_jetty.jetty_security.JDBCLoginServiceDriver
                url=jdbc:jetty-security-test:realm
                username=jetty
                password=secret
                usertable=users
                usertablekey=id
                usertableuserfield=name
                usertablepasswordfield=password
                roletable=roles
                roletablekey=id
                roletablerolefield=role
                userroletable=user_roles
                userroletableuserkey=user_id
                userroletablerolekey=role_id
                cachetime=1
                """;
    }
}
