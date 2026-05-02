/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Mapper;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.util.FileNameMapper;
import org.apache.tools.ant.util.FlatFileNameMapper;
import org.apache.tools.ant.util.IdentityMapper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapperTest {
    private static final String NESTED_FILE_NAME = "nested/input.txt";

    @Test
    void createsMapperImplementationFromPredefinedType() {
        Mapper mapper = new Mapper(newProject());
        Mapper.MapperType mapperType = new Mapper.MapperType();
        mapperType.setValue("flatten");
        mapper.setType(mapperType);

        FileNameMapper implementation = mapper.getImplementation();

        assertThat(implementation).isInstanceOf(FlatFileNameMapper.class);
        assertThat(implementation.mapFileName(NESTED_FILE_NAME)).containsExactly("input.txt");
    }

    @Test
    void createsMapperImplementationThroughConfiguredAntClasspath() {
        Project project = newProject();
        Mapper mapper = new Mapper(project);
        mapper.setClasspath(new Path(project));
        mapper.setClassname(IdentityMapper.class.getName());

        try {
            FileNameMapper implementation = mapper.getImplementation();

            assertThat(implementation).isInstanceOf(IdentityMapper.class);
            assertThat(implementation.mapFileName(NESTED_FILE_NAME)).containsExactly(NESTED_FILE_NAME);
        } catch (BuildException exception) {
            if (!isWrappedNativeImageUnsupportedFeatureError(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    private static boolean isWrappedNativeImageUnsupportedFeatureError(BuildException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) cause);
    }
}
