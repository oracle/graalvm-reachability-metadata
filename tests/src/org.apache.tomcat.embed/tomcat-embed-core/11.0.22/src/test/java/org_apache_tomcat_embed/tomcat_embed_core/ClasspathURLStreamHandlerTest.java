/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.InputStream;
import java.net.URL;

import org.apache.catalina.webresources.ClasspathURLStreamHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClasspathURLStreamHandlerTest {

    @Test
    void opensClasspathResourceThroughUrlConnection() throws Exception {
        URL url = new URL(null, "classpath:server-embed.xml", new ClasspathURLStreamHandler());

        try (InputStream input = url.openConnection().getInputStream()) {
            assertThat(input.readAllBytes()).isNotEmpty();
        }
    }
}
