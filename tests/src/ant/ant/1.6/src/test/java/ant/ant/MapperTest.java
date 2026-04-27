/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Mapper;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.util.FileNameMapper;
import org.apache.tools.ant.util.GlobPatternMapper;
import org.apache.tools.ant.util.IdentityMapper;
import org.junit.jupiter.api.Test;

public class MapperTest {
    @Test
    void createsBuiltInMapperWithApplicationClassLoader() {
        Mapper mapper = new Mapper(new Project());
        Mapper.MapperType mapperType = new Mapper.MapperType();
        mapperType.setValue("glob");
        mapper.setType(mapperType);
        mapper.setFrom("*.java");
        mapper.setTo("*.class");

        FileNameMapper implementation = mapper.getImplementation();

        assertThat(implementation).isInstanceOf(GlobPatternMapper.class);
        assertThat(implementation.mapFileName("Mapper.java")).containsExactly("Mapper.class");
        assertThat(implementation.mapFileName("README.md")).isNull();
    }

    @Test
    void createsMapperWithProjectClassLoader() {
        Project project = new DelegatingProject(IdentityMapper.class);
        Mapper mapper = new Mapper(project);
        mapper.setClassname(IdentityMapper.class.getName());
        mapper.setClasspath(new Path(project));

        FileNameMapper implementation = mapper.getImplementation();

        assertThat(implementation).isInstanceOf(IdentityMapper.class);
        assertThat(implementation.mapFileName("src/main/ant.xml")).containsExactly("src/main/ant.xml");
    }

    private static final class DelegatingProject extends Project {
        private final Class<?> targetClass;

        private DelegatingProject(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public AntClassLoader createClassLoader(Path path) {
            return new DelegatingAntClassLoader(targetClass);
        }
    }

    private static final class DelegatingAntClassLoader extends AntClassLoader {
        private final Class<?> targetClass;

        private DelegatingAntClassLoader(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        protected synchronized Class<?> loadClass(String classname, boolean resolve) throws ClassNotFoundException {
            if (targetClass.getName().equals(classname)) {
                return targetClass;
            }
            return super.loadClass(classname, resolve);
        }
    }
}
