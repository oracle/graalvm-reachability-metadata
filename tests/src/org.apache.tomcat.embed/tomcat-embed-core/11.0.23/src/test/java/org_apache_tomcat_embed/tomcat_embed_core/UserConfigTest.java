/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.UserConfig;
import org.apache.catalina.startup.UserDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class UserConfigTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void deploysConfiguredUserWebApplication() throws Exception {
        Path userHome = temporaryDirectory.resolve("alice");
        Files.createDirectories(userHome.resolve("public_html/WEB-INF"));
        TestUserDatabase.userHome = userHome;

        UserConfig userConfig = new UserConfig();
        userConfig.setUserClass(TestUserDatabase.class.getName());
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(temporaryDirectory.resolve("base").toString());
        tomcat.setPort(0);
        Host host = tomcat.getHost();
        host.addLifecycleListener(userConfig);

        try {
            tomcat.start();

            assertThat(host.findChild("/~alice")).isNotNull();
            assertThat(host.findChild("/~alice").getState()).isEqualTo(LifecycleState.STARTED);
        } finally {
            TestUserDatabase.userHome = null;
            if (tomcat.getServer().getState().isAvailable()) {
                tomcat.stop();
            }
            if (tomcat.getServer().getState() != LifecycleState.DESTROYED) {
                tomcat.destroy();
            }
        }
    }

    public static class TestUserDatabase implements UserDatabase {
        private static Path userHome;
        private UserConfig userConfig;

        @Override
        public UserConfig getUserConfig() {
            return userConfig;
        }

        @Override
        public void setUserConfig(UserConfig userConfig) {
            this.userConfig = userConfig;
        }

        @Override
        public String getHome(String user) {
            return userHome.toString();
        }

        @Override
        public Enumeration<String> getUsers() {
            return Collections.enumeration(Collections.singleton("alice"));
        }
    }
}
