/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.startup.Catalina;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalinaTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsGeneratedCodeProviderBeforeParsingServerXml() throws Exception {
        Path serverXml = temporaryDirectory.resolve("server.xml");
        Files.writeString(serverXml, "<Server port=\"-1\" shutdown=\"SHUTDOWN\"/>");
        Catalina catalina = new Catalina();
        catalina.setConfigFile(serverXml.toUri().toString());
        catalina.setUseGeneratedCode(true);
        catalina.setGeneratedCodePackage(getClass().getPackageName());

        try {
            catalina.load();

            assertThat(catalina.getServer()).isNotNull();
            assertThat(DigesterGeneratedCodeLoader.getRequestedClassName())
                    .isEqualTo(getClass().getPackageName() + ".ServerXml");
        } finally {
            destroy(catalina.getServer());
        }
    }

    private static void destroy(Server server) throws Exception {
        if (server != null && server.getState() != LifecycleState.DESTROYED) {
            server.destroy();
        }
    }
}
