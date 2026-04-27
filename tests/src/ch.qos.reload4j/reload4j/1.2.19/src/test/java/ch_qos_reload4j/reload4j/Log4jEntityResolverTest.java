/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.log4j.xml.Log4jEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class Log4jEntityResolverTest {
    private static final String LOG4J_DTD_PUBLIC_ID = "-//APACHE//DTD LOG4J 1.2//EN";

    @Test
    void resolvesLog4jDtdSystemIdToInputSource() {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource source = resolver.resolveEntity(LOG4J_DTD_PUBLIC_ID, "https://example.invalid/log4j.dtd");

        assertThat(source).isNotNull();
        InputStream byteStream = source.getByteStream();
        assertThat(byteStream).isNotNull();
    }
}
