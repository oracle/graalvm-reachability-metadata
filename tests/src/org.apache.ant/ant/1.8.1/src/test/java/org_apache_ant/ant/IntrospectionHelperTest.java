/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperTest {
    @Test
    void configuresAttributesNestedElementsAndText() {
        Project project = new Project();
        ConfigurableElement element = new ConfigurableElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(ConfigurableElement.class);

        helper.setAttribute(project, element, "projectValue", "configured by project");
        helper.setAttribute(project, element, "stringValue", "configured by string");
        helper.addText(project, element, "Ant configuration");

        IntrospectionHelper.Creator configuredNoArg = helper.getElementCreator(
                project, "", element, "noarg", null);
        ConfiguredNoArg configuredNoArgValue = (ConfiguredNoArg) configuredNoArg.create();
        configuredNoArg.store();

        IntrospectionHelper.Creator configuredProject = helper.getElementCreator(
                project, "", element, "project", null);
        ConfiguredProject configuredProjectValue = (ConfiguredProject) configuredProject.create();
        configuredProject.store();

        IntrospectionHelper.Creator addedNoArg = helper.getElementCreator(
                project, "", element, "addednoarg", null);
        AddedNoArg addedNoArgValue = (AddedNoArg) addedNoArg.create();
        addedNoArg.store();

        IntrospectionHelper.Creator addedProject = helper.getElementCreator(
                project, "", element, "addedproject", null);
        AddedProject addedProjectValue = (AddedProject) addedProject.create();
        addedProject.store();

        assertThat(element.projectValue.value).isEqualTo("configured by project");
        assertThat(element.projectValue.project).isSameAs(project);
        assertThat(element.stringValue.value).isEqualTo("configured by string");
        assertThat(element.text).isEqualTo("Ant configuration");
        assertThat(element.configuredNoArg).isSameAs(configuredNoArgValue);
        assertThat(element.configuredProject).isSameAs(configuredProjectValue);
        assertThat(element.configuredProject.project).isSameAs(project);
        assertThat(element.addedNoArg).isSameAs(addedNoArgValue);
        assertThat(element.addedProject).isSameAs(addedProjectValue);
        assertThat(element.addedProject.project).isSameAs(project);
    }

    public static class ConfigurableElement {
        private ProjectStringValue projectValue;
        private StringValue stringValue;
        private String text;
        private ConfiguredNoArg configuredNoArg;
        private ConfiguredProject configuredProject;
        private AddedNoArg addedNoArg;
        private AddedProject addedProject;

        public void setProjectValue(ProjectStringValue value) {
            projectValue = value;
        }

        public void setStringValue(StringValue value) {
            stringValue = value;
        }

        public void addText(String value) {
            text = value;
        }

        public void addConfiguredNoArg(ConfiguredNoArg value) {
            configuredNoArg = value;
        }

        public void addConfiguredProject(ConfiguredProject value) {
            configuredProject = value;
        }

        public void addAddedNoArg(AddedNoArg value) {
            addedNoArg = value;
        }

        public void addAddedProject(AddedProject value) {
            addedProject = value;
        }
    }

    public static class ProjectStringValue {
        private final Project project;
        private final String value;

        public ProjectStringValue(Project project, String value) {
            this.project = project;
            this.value = value;
        }
    }

    public static class StringValue {
        private final String value;

        public StringValue(String value) {
            this.value = value;
        }
    }

    public static class ConfiguredNoArg {
        public ConfiguredNoArg() {
        }
    }

    public static class ConfiguredProject {
        private final Project project;

        public ConfiguredProject(Project project) {
            this.project = project;
        }
    }

    public static class AddedNoArg {
        public AddedNoArg() {
        }
    }

    public static class AddedProject {
        private final Project project;

        public AddedProject(Project project) {
            this.project = project;
        }
    }
}
