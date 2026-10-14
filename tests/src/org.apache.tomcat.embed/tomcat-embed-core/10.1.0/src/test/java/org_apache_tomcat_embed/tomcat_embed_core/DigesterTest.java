/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;

import static org.assertj.core.api.Assertions.assertThat;

@Order(-1)
public class DigesterTest {

    private static final String PROPERTY_SOURCE = "org.apache.tomcat.util.digester.PROPERTY_SOURCE";

    @Test
    void parsesXmlUsingConfiguredPropertySource() throws Exception {
        String originalSource = System.getProperty(PROPERTY_SOURCE);
        System.setProperty(PROPERTY_SOURCE, TestPropertySource.class.getName());

        AtomicReference<String> parsedMessage = new AtomicReference<>();
        Digester digester = new Digester();
        digester.addRule("message", new Rule() {
            @Override
            public void begin(String namespace, String name, Attributes attributes) {
                parsedMessage.set(attributes.getValue("text"));
            }
        });

        try {
            String xml = "<message text=\"${message}\"/>";
            digester.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } finally {
            if (originalSource == null) {
                System.clearProperty(PROPERTY_SOURCE);
            } else {
                System.setProperty(PROPERTY_SOURCE, originalSource);
            }
        }

        assertThat(parsedMessage.get()).isEqualTo("resolved-message");
    }

    public static final class TestPropertySource implements IntrospectionUtils.PropertySource {
        @Override
        public String getProperty(String key) {
            return "message".equals(key) ? "resolved-message" : null;
        }
    }
}
