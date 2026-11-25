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
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.util.CustomObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomObjectInputStreamTest {
    @Test
    void resolvesPersistedApplicationValuesUsingConfiguredLoader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new ArrayList<>(List.of("book", "pen")));
        }
        try (CustomObjectInputStream input = new CustomObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), getClass().getClassLoader())) {
            assertThat(input.readObject()).isEqualTo(List.of("book", "pen"));
        }
    }
}
