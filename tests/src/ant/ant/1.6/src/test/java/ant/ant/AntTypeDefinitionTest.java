/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Mapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AntTypeDefinitionTest {
    @Test
    void createsNoArgTypeFromConfiguredClassName() {
        Project project = new Project();
        AntTypeDefinition definition = new AntTypeDefinition();
        definition.setName("fileset");
        definition.setClassName(FileSet.class.getName());

        Object created = definition.create(project);

        assertThat(created).isInstanceOf(FileSet.class);
        assertThat(((FileSet) created).getProject()).isSameAs(project);
        assertThat(definition.getTypeClass(project)).isSameAs(FileSet.class);
        assertThat(definition.getExposedClass(project)).isSameAs(FileSet.class);
    }

    @Test
    void createsProjectConstructedTypeThroughConfiguredClassLoader() {
        Project project = new Project();
        AntTypeDefinition definition = new AntTypeDefinition();
        definition.setName("mapper");
        definition.setClassName(Mapper.class.getName());
        definition.setClassLoader(AntTypeDefinitionTest.class.getClassLoader());

        Object created = definition.create(project);

        assertThat(created).isInstanceOf(Mapper.class);
        assertThat(((Mapper) created).getProject()).isSameAs(project);
        assertThat(definition.getTypeClass(project)).isSameAs(Mapper.class);
        assertThat(definition.getExposedClass(project)).isSameAs(Mapper.class);
    }
}
