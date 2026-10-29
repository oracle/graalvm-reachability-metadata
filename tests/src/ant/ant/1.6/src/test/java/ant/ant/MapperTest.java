/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Mapper;
import org.apache.tools.ant.util.FileNameMapper;
import org.junit.jupiter.api.Test;

public class MapperTest {
    @Test
    void instantiatesConfiguredGlobMapper() {
        Mapper mapper = new Mapper(new Project());
        mapper.setClassname("org.apache.tools.ant.util.GlobPatternMapper");
        mapper.setFrom("*.java");
        mapper.setTo("*.class");
        FileNameMapper implementation = mapper.getImplementation();
        assertArrayEquals(new String[] {"Example.class"}, implementation.mapFileName("Example.java"));
        assertNull(implementation.mapFileName("notes.txt"));
    }
}
