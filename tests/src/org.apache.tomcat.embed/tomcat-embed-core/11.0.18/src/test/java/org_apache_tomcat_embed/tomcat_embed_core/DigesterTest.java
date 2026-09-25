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

import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;

import static org.assertj.core.api.Assertions.assertThat;

public class DigesterTest {

    @Test
    void parsesXmlUsingConfiguredPropertySource() throws Exception {
        String propertyName = "tomcat.test.digester.message";
        String originalValue = System.getProperty(propertyName);
        System.setProperty(propertyName, "resolved-message");

        AtomicReference<String> parsedMessage = new AtomicReference<>();
        Digester digester = new Digester();
        digester.addRule("message", new Rule() {
            @Override
            public void begin(String namespace, String name, Attributes attributes) {
                parsedMessage.set(attributes.getValue("text"));
            }
        });

        try {
            String xml = "<message text=\"${" + propertyName + "}\"/>";
            digester.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } finally {
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }

        assertThat(parsedMessage.get()).isEqualTo("resolved-message");
    }
}
