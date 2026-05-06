/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.log4j.xml.Log4jEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4jEntityResolverTest {
    @Test
    void resolvesBundledLog4jDtdResource() throws Exception {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource source = resolver.resolveEntity("-//APACHE//DTD LOG4J 1.2//EN", "log4j.dtd");

        assertThat(source).isNotNull();
        try (InputStream byteStream = source.getByteStream()) {
            assertThat(byteStream).isNotNull();
            String dtd = new String(byteStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(dtd).contains("<!ELEMENT log4j:configuration");
        }
    }

    @Test
    void ignoresUnrecognizedEntities() {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource source = resolver.resolveEntity("-//EXAMPLE//DTD OTHER//EN", "other.dtd");

        assertThat(source).isNull();
    }
}
