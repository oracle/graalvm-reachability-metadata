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

import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;

import static org.assertj.core.api.Assertions.assertThat;

public class DigesterFactoryTest {

    @Test
    void createsConfiguredDigesterForApplicationXml() throws Exception {
        AtomicReference<String> value = new AtomicReference<>();
        Digester digester = DigesterFactory.newDigester(false, true, null, true);
        digester.addRule("configuration", new Rule() {
            @Override
            public void begin(String namespace, String name, Attributes attributes) {
                value.set(attributes.getValue("value"));
            }
        });

        digester.parse(new ByteArrayInputStream(
                "<configuration value=\"ready\"/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(value).hasValue("ready");
    }
}
