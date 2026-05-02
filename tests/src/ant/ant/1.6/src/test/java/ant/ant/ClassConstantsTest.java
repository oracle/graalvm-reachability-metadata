/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.io.StringReader;

import org.apache.tools.ant.filters.ClassConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassConstantsTest {
    @Test
    void readUsesJavaClassHelperToDecodeClassBytes() {
        ClassConstants filter = new ClassConstants(new StringReader("not a Java class file"));

        assertThatThrownBy(() -> filter.read())
                .isInstanceOf(IOException.class);
    }
}
