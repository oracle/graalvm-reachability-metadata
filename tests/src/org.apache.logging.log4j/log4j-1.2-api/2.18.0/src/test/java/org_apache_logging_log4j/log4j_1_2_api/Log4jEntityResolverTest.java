/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.apache.log4j.xml.Log4jEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class Log4jEntityResolverTest {

    @Test
    void resolvesLog4jDtdFromClasspathResource() throws IOException {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(null, "https://example.invalid/log4j.dtd");

        assertThat(inputSource).isNotNull();
        try (InputStream byteStream = inputSource.getByteStream()) {
            assertThat(byteStream).isNotNull();
            assertThat(byteStream.readAllBytes()).isNotNull();
        }
    }

    @Test
    void ignoresUnrelatedExternalEntity() {
        Log4jEntityResolver resolver = new Log4jEntityResolver();

        InputSource inputSource = resolver.resolveEntity(null, "https://example.invalid/not-logging.dtd");

        assertThat(inputSource).isNull();
    }
}
