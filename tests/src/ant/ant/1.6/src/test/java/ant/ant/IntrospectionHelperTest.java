/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionHelperTest {
    @Test
    void configuresAttributesTextAndNestedElementsThroughPublicAntIntrospection() {
        Project project = new Project();
        IntrospectedElement parent = new IntrospectedElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                IntrospectedElement.class);

        helper.setAttribute(project, parent, "custom", "custom-value");
        helper.addText(project, parent, "nested text");
        Object configuredNoArg = helper.createElement(project, parent, "configurednoarg");
        helper.storeElement(project, parent, configuredNoArg, "configurednoarg");
        Object configuredProject = helper.createElement(project, parent, "configuredproject");
        helper.storeElement(project, parent, configuredProject, "configuredproject");
        Object addedNoArg = helper.createElement(project, parent, "addednoarg");
        Object addedProject = helper.createElement(project, parent, "addedproject");

        assertThat(helper.getAttributeType("custom")).isSameAs(CustomAttribute.class);
        assertThat(helper.getElementType("configurednoarg")).isSameAs(ConfiguredNoArgChild.class);
        assertThat(helper.getElementType("configuredproject"))
                .isSameAs(ConfiguredProjectChild.class);
        assertThat(helper.getElementType("addednoarg")).isSameAs(AddedNoArgChild.class);
        assertThat(helper.getElementType("addedproject")).isSameAs(AddedProjectChild.class);
        assertThat(parent.customAttribute.value).isEqualTo("custom-value");
        assertThat(parent.text).isEqualTo("nested text");
        assertThat(parent.configuredNoArgChild).isSameAs(configuredNoArg);
        assertThat(parent.configuredProjectChild).isSameAs(configuredProject);
        assertThat(parent.configuredProjectChild.project).isSameAs(project);
        assertThat(parent.addedNoArgChild).isSameAs(addedNoArg);
        assertThat(parent.addedProjectChild).isSameAs(addedProject);
        assertThat(parent.addedProjectChild.project).isSameAs(project);
    }

    public static class IntrospectedElement {
        private CustomAttribute customAttribute;
        private String text;
        private ConfiguredNoArgChild configuredNoArgChild;
        private ConfiguredProjectChild configuredProjectChild;
        private AddedNoArgChild addedNoArgChild;
        private AddedProjectChild addedProjectChild;

        public void setCustom(CustomAttribute customAttribute) {
            this.customAttribute = customAttribute;
        }

        public void addText(String text) {
            this.text = text;
        }

        public void addConfiguredConfiguredNoArg(ConfiguredNoArgChild child) {
            this.configuredNoArgChild = child;
        }

        public void addConfiguredConfiguredProject(ConfiguredProjectChild child) {
            this.configuredProjectChild = child;
        }

        public void addAddedNoArg(AddedNoArgChild child) {
            this.addedNoArgChild = child;
        }

        public void addAddedProject(AddedProjectChild child) {
            this.addedProjectChild = child;
        }
    }

    public static class CustomAttribute {
        private final String value;

        public CustomAttribute(String value) {
            this.value = value;
        }
    }

    public static class ConfiguredNoArgChild {
        public ConfiguredNoArgChild() {
        }
    }

    public static class ConfiguredProjectChild {
        private final Project project;

        public ConfiguredProjectChild(Project project) {
            this.project = project;
        }
    }

    public static class AddedNoArgChild {
        public AddedNoArgChild() {
        }
    }

    public static class AddedProjectChild {
        private final Project project;

        public AddedProjectChild(Project project) {
            this.project = project;
        }
    }
}
