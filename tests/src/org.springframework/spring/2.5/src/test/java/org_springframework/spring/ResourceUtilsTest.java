/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceUtilsTest {
    @Test
    void resolvesClasspathUrlAndReadsNamespaceMappings() throws Exception {
        URL url = ResourceUtils.getURL("classpath:META-INF/spring.handlers");
        Properties mappings = new Properties();
        try (InputStream stream = url.openStream()) {
            mappings.load(stream);
        }
        assertEquals("org.springframework.context.config.ContextNamespaceHandler",
                mappings.getProperty("http://www.springframework.org/schema/context"));
    }
}
