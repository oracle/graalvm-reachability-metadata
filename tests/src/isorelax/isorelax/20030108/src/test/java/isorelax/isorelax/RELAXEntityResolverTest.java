/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package isorelax.isorelax;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import jp.gr.xml.relax.sax.RELAXEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class RELAXEntityResolverTest {
    @Test
    void resolvesBundledRelaxDtdsByTheirPublishedIdentifiers() throws Exception {
        RELAXEntityResolver resolver = new RELAXEntityResolver();

        InputSource core =
                resolver.resolveEntity(
                        null, "http://www.xml.gr.jp/relax/core1/relaxCore.dtd");
        InputSource namespace =
                resolver.resolveEntity("-//RELAX//DTD RELAX Namespace 1.0//JA", null);
        InputSource grammar =
                resolver.resolveEntity("-//RELAX//DTD RELAX Grammar 1.0//JA", null);

        assertThat(read(core)).contains("DTD for RELAX Core");
        assertThat(read(namespace)).contains("DTD for RELAX Namespace");
        assertThat(read(grammar)).contains("%relaxCore;", "%relaxNamespace;");
    }

    private static String read(InputSource source) throws IOException {
        assertThat(source).isNotNull();
        assertThat(source.getSystemId()).isNotBlank();
        try (InputStream stream = URI.create(source.getSystemId()).toURL().openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
