/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.log4j.xml.Log4jEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4jEntityResolverTest {

    private static final String LOG4J_PUBLIC_ID = "-//APACHE//DTD LOG4J 1.2//EN";

    @Test
    void resolvesEmbeddedDtdWhenSystemIdTargetsLog4jDtd() throws IOException {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(null, "file:///log4j.dtd");

        assertThat(inputSource).isNotNull();
        assertThat(readContents(inputSource)).contains("<!ELEMENT log4j:configuration");
    }

    @Test
    void resolvesEmbeddedDtdWhenPublicIdMatchesLog4jDtd() throws IOException {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(LOG4J_PUBLIC_ID, "http://example.invalid/other.dtd");

        assertThat(inputSource).isNotNull();
        assertThat(readContents(inputSource)).contains("<!ELEMENT log4j:configuration");
    }

    @Test
    void returnsNullForUnrelatedEntityIdentifiers() {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity("-//EXAMPLE//DTD OTHER 1.0//EN", "http://example.invalid/other.dtd");

        assertThat(inputSource).isNull();
    }

    private static String readContents(InputSource inputSource) throws IOException {
        try (InputStream inputStream = inputSource.getByteStream()) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
