/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.ClassConstants;
import org.apache.tools.ant.types.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassConstantsTest {
    private static final byte[] CLASS_WITH_CONSTANT_BYTES = new byte[] {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
            0x00, 0x03, 0x00, 0x2D,
            0x00, 0x09,
            0x01, 0x00, 0x0F,
            'C', 'o', 'n', 's', 't', 'a', 'n', 't', 'F', 'i', 'x', 't', 'u', 'r', 'e',
            0x07, 0x00, 0x01,
            0x01, 0x00, 0x10,
            'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', 'O', 'b', 'j', 'e', 'c', 't',
            0x07, 0x00, 0x03,
            0x01, 0x00, 0x06,
            'A', 'N', 'S', 'W', 'E', 'R',
            0x01, 0x00, 0x01,
            'I',
            0x03, 0x00, 0x00, 0x00, 0x07,
            0x01, 0x00, 0x0D,
            'C', 'o', 'n', 's', 't', 'a', 'n', 't', 'V', 'a', 'l', 'u', 'e',
            0x00, 0x21,
            0x00, 0x02,
            0x00, 0x04,
            0x00, 0x00,
            0x00, 0x01,
            0x00, 0x19,
            0x00, 0x05,
            0x00, 0x06,
            0x00, 0x01,
            0x00, 0x08,
            0x00, 0x00, 0x00, 0x02,
            0x00, 0x07,
            0x00, 0x00,
            0x00, 0x00
    };

    @Test
    void readReturnsConstantsFromClassFileBytes() throws IOException {
        ClassConstants filter = new ClassConstants(new StringReader(classFileText()));

        String constants = readFully(filter);

        assertThat(constants).isEqualTo("ANSWER=7" + System.lineSeparator());
    }

    @Test
    void readInitializesByteArrayParameterTypeInFreshAntClassLoader() throws Exception {
        try {
            Reader filter = newIsolatedClassConstantsFilter();

            String constants = readFully(filter);

            assertThat(constants).isEqualTo("ANSWER=7" + System.lineSeparator());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Reader newIsolatedClassConstantsFilter() throws Exception {
        Project project = new Project();
        Path classpath = new Path(project, System.getProperty("java.class.path"));
        AntClassLoader classLoader = new AntClassLoader(project, classpath, false);
        Class<?> filterClass = classLoader.forceLoadClass(ClassConstants.class.getName());
        Constructor<?> constructor = filterClass.getConstructor(Reader.class);
        return (Reader) constructor.newInstance(new StringReader(classFileText()));
    }

    private static String classFileText() {
        return new String(CLASS_WITH_CONSTANT_BYTES, StandardCharsets.ISO_8859_1);
    }

    private static String readFully(Reader filter) throws IOException {
        StringBuilder result = new StringBuilder();
        int ch = filter.read();
        while (ch != -1) {
            result.append((char) ch);
            ch = filter.read();
        }
        return result.toString();
    }
}
