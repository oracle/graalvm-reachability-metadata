/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import org.apache.juli.ClassLoaderLogManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderLogManagerTest {

    @Test
    void createsHandlersDeclaredInLoggingConfiguration() throws Exception {
        ClassLoaderLogManager manager = new ClassLoaderLogManager();
        byte[] configuration = "handlers=java.util.logging.ConsoleHandler\n"
                .getBytes(StandardCharsets.UTF_8);

        try {
            manager.readConfiguration(new ByteArrayInputStream(configuration));
            Logger rootLogger = manager.getLogger("");

            assertThat(rootLogger).isNotNull();
            assertThat(rootLogger.getHandlers()).hasSize(1);
            assertThat(rootLogger.getHandlers()[0]).isInstanceOf(ConsoleHandler.class);
        } finally {
            manager.shutdown();
        }
    }
}
