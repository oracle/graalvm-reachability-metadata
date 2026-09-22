/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DigesterTest {

    @Test
    void parsesXmlWithPushedRootObject() throws Exception {
        Digester digester = new Digester();
        StringBuilder root = new StringBuilder("root");
        digester.push(root);

        try (ByteArrayInputStream input =
                new ByteArrayInputStream("<configuration/>".getBytes(StandardCharsets.UTF_8))) {
            Object parsedRoot = digester.parse(input);
            assertThat(parsedRoot).isSameAs(root);
        }
    }
}
