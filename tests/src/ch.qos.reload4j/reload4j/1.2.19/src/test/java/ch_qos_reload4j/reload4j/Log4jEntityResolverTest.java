/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.log4j.xml.Log4jEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class Log4jEntityResolverTest {
    private static final String LOG4J_PUBLIC_ID = "-//APACHE//DTD LOG4J 1.2//EN";

    @Test
    void resolvesEmbeddedDtdWhenSystemIdEndsWithLog4jDtd() throws Exception {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(null, "https://example.invalid/path/log4j.dtd");

        assertThat(readDtd(inputSource))
                .contains("<!ELEMENT log4j:configuration")
                .contains("<!ELEMENT appender");
    }

    @Test
    void resolvesEmbeddedDtdWhenPublicIdMatchesLog4jDtd() throws Exception {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(LOG4J_PUBLIC_ID, "https://example.invalid/other.dtd");

        assertThat(readDtd(inputSource))
                .contains("<!ELEMENT log4j:configuration")
                .contains("<!ELEMENT root");
    }

    @Test
    void returnsNullForUnrelatedEntity() {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(null, "https://example.invalid/unrelated.dtd");

        assertThat(inputSource).isNull();
    }

    private static String readDtd(InputSource inputSource) throws Exception {
        assertThat(inputSource).isNotNull();
        try (InputStream inputStream = inputSource.getByteStream()) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
