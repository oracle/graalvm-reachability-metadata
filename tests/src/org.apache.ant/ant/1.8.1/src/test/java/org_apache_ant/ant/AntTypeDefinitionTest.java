/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class AntTypeDefinitionTest {
    @Test
    void createsTypesLoadedByDefaultAndConfiguredClassLoaders() {
        Project project = new Project();
        project.initProperties();

        AntTypeDefinition defaultLoadedDefinition = new AntTypeDefinition();
        defaultLoadedDefinition.setName("default-loaded");
        defaultLoadedDefinition.setClassName(DefaultConstructedType.class.getName());

        assertThat(defaultLoadedDefinition.getTypeClass(project))
                .isEqualTo(DefaultConstructedType.class);
        assertThat(defaultLoadedDefinition.create(project))
                .isInstanceOf(DefaultConstructedType.class);

        AntTypeDefinition projectConstructedDefinition = new AntTypeDefinition();
        projectConstructedDefinition.setName("project-constructed");
        projectConstructedDefinition.setClassName(ProjectConstructedType.class.getName());
        projectConstructedDefinition.setClassLoader(getClass().getClassLoader());

        assertThat(projectConstructedDefinition.getTypeClass(project))
                .isEqualTo(ProjectConstructedType.class);
        assertThat(projectConstructedDefinition.create(project))
                .isInstanceOfSatisfying(ProjectConstructedType.class,
                        type -> assertThat(type.project).isSameAs(project));
    }

    public static class DefaultConstructedType {
        public DefaultConstructedType() {
        }
    }

    public static class ProjectConstructedType {
        private final Project project;

        public ProjectConstructedType(Project project) {
            this.project = project;
        }
    }
}
