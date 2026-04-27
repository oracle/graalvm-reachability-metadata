/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperTest {
    @Test
    void introspectsNestedElementsAttributesAndText() {
        Project project = new Project();
        IntrospectedElement element = new IntrospectedElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, IntrospectedElement.class);

        helper.addText(project, element, "inline text");
        helper.setAttribute(project, element, "custom", "custom value");
        helper.setAttribute(project, element, "implementationclass", String.class.getName());

        Object configuredDefault = helper.createElement(project, element, "configureddefaultchild");
        helper.storeElement(project, element, configuredDefault, "configureddefaultchild");
        Object configuredProject = helper.createElement(project, element, "configuredprojectchild");
        helper.storeElement(project, element, configuredProject, "configuredprojectchild");
        Object addedDefault = helper.createElement(project, element, "defaultchild");
        Object addedProject = helper.createElement(project, element, "projectchild");

        assertThat(element.text).isEqualTo("inline text");
        assertThat(element.customAttribute.value).isEqualTo("custom value");
        assertThat(element.implementationClass).isSameAs(String.class);
        assertThat(element.configuredChildren).containsExactly(configuredDefault, configuredProject);
        assertThat(element.addedChildren).containsExactly(addedDefault, addedProject);
        assertThat(configuredDefault).isInstanceOf(ConfiguredDefaultChild.class);
        assertThat(configuredProject).isInstanceOf(ConfiguredProjectChild.class);
        assertThat(addedDefault).isInstanceOf(DefaultChild.class);
        assertThat(addedProject).isInstanceOf(ProjectChild.class);
        assertThat(((ConfiguredProjectChild) configuredProject).project).isSameAs(project);
        assertThat(((ProjectChild) addedProject).project).isSameAs(project);
    }

    public static final class IntrospectedElement {
        private final List<Object> configuredChildren = new ArrayList<>();
        private final List<Object> addedChildren = new ArrayList<>();
        private String text;
        private CustomAttribute customAttribute;
        private Class<?> implementationClass;

        public void addText(String text) {
            this.text = text;
        }

        public void setCustom(CustomAttribute customAttribute) {
            this.customAttribute = customAttribute;
        }

        public void setImplementationClass(Class<?> implementationClass) {
            this.implementationClass = implementationClass;
        }

        public void addConfiguredConfiguredDefaultChild(ConfiguredDefaultChild child) {
            configuredChildren.add(child);
        }

        public void addConfiguredConfiguredProjectChild(ConfiguredProjectChild child) {
            configuredChildren.add(child);
        }

        public void addDefaultChild(DefaultChild child) {
            addedChildren.add(child);
        }

        public void addProjectChild(ProjectChild child) {
            addedChildren.add(child);
        }
    }

    public static final class CustomAttribute {
        private final String value;

        public CustomAttribute(String value) {
            this.value = value;
        }
    }

    public static final class ConfiguredDefaultChild {
        public ConfiguredDefaultChild() {
        }
    }

    public static final class ConfiguredProjectChild {
        private final Project project;

        public ConfiguredProjectChild(Project project) {
            this.project = project;
        }
    }

    public static final class DefaultChild {
        public DefaultChild() {
        }
    }

    public static final class ProjectChild {
        private final Project project;

        public ProjectChild(Project project) {
            this.project = project;
        }
    }
}
