/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import org.apache.tools.ant.filters.ClassConstants;
import org.junit.jupiter.api.Test;

public class ClassConstantsTest {
    @Test
    void delegatesConstantExtractionToTheClassHelper() {
        ClassConstants classConstants = new ClassConstants(new StringReader("not-a-class-file"));

        assertThatThrownBy(classConstants::read).isInstanceOf(IOException.class);
    }
}
