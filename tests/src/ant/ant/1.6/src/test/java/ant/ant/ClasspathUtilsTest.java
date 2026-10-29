/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.apache.tools.ant.util.ClasspathUtils;
import org.apache.tools.ant.util.FileNameMapper;
import org.junit.jupiter.api.Test;

public class ClasspathUtilsTest {
    @Test
    void constructsClasspathComponentByName() {
        FileNameMapper mapper = (FileNameMapper) ClasspathUtils.newInstance(
                "org.apache.tools.ant.util.FlatFileNameMapper", getClass().getClassLoader());
        assertArrayEquals(new String[] {"input.txt"}, mapper.mapFileName("folder/input.txt"));
    }
}
