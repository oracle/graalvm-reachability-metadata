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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomObjectInputStreamTest {
    @Test
    void deserializesApplicationDataWithTheSuppliedLoader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new ArrayList<>(List.of("one", "two")));
        }
        try (CustomObjectInputStream input = new CustomObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()), getClass().getClassLoader())) {
            assertEquals(List.of("one", "two"), input.readObject());
        }
    }
}
