/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.apache.tools.ant.filters.ClassConstants;
import org.junit.jupiter.api.Test;

public class ClassConstantsTest {
    @Test
    void delegatesConstantExtractionToTheClassHelper() throws IOException {
        ClassConstants classConstants = new ClassConstants(new StringReader("constant-input"));

        assertThat(readFully(classConstants)).isEqualTo("payload=constant-input" + System.lineSeparator());
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            result.append((char) character);
        }
        return result.toString();
    }
}
