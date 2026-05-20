/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomObjectInputStreamTest {

    @Test
    void resolvesClassesWithProvidedClassLoader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject("hello");
        }

        try (CustomObjectInputStream stream = new CustomObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), getClass().getClassLoader(),
                LogFactory.getLog(CustomObjectInputStreamTest.class), null, false)) {
            assertThat(stream.readObject()).isEqualTo("hello");
        }
    }
}
