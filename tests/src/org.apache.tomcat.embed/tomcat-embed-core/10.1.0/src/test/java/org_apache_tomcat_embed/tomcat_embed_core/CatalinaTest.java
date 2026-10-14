/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.startup.Catalina;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalinaTest {

    @Test
    void loadsGeneratedCodeProvider() throws Exception {
        Catalina generatedCatalina = new Catalina();
        generatedCatalina.setConfigFile(Catalina.SERVER_XML);
        generatedCatalina.setUseGeneratedCode(true);
        generatedCatalina.setGeneratedCodePackage(getClass().getPackageName());

        try {
            generatedCatalina.load();

            assertThat(generatedCatalina.getServer()).isNotNull();
            assertThat(DigesterGeneratedCodeLoader.getRequestedClassName())
                    .isEqualTo(getClass().getPackageName() + ".ServerXml");
        } finally {
            destroy(generatedCatalina.getServer());
            Digester.setGeneratedCodeLoader(null);
        }
    }

    private static void destroy(Server server) throws Exception {
        if (server != null && server.getState() != LifecycleState.DESTROYED) {
            server.destroy();
        }
    }
}
