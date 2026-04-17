/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_jsonwebtoken.jjwt_api;

import io.jsonwebtoken.lang.Classes;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassesExceptionIgnoringAccessorTest {

    private static final String TEST_RESOURCE = "io_jsonwebtoken/jjwt_api/classes-exception-ignoring-accessor.txt";

    @Test
    void getResourceReturnsUrlForClasspathResource() {
        URL resourceUrl = Classes.invokeStatic(
            Classes.class,
            "getResource",
            new Class<?>[]{String.class},
            TEST_RESOURCE
        );

        assertThat(resourceUrl).isNotNull();
        assertThat(resourceUrl.toExternalForm()).contains(TEST_RESOURCE);
    }

    @Test
    void getResourceAsStreamReturnsStreamForClasspathResource() throws Exception {
        try (InputStream inputStream = Classes.getResourceAsStream(TEST_RESOURCE)) {
            assertThat(inputStream).isNotNull();
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("resource-loaded-through-classes");
        }
    }
}
