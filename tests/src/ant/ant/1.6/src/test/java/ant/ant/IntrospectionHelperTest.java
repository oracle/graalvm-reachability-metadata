/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperTest {

    @Test
    void configuresAttributesTextAndNestedElementsUsingPublicBeanConventions() {
        Project project = new Project();
        AttributeAndTextBean attributeAndTextBean = new AttributeAndTextBean();
        IntrospectionHelper attributeHelper = IntrospectionHelper.getHelper(project, AttributeAndTextBean.class);

        attributeHelper.setAttribute(project, attributeAndTextBean, "value", "configured");
        attributeHelper.addText(project, attributeAndTextBean, "nested text");

        assertThat(attributeAndTextBean.value.value).isEqualTo("configured");
        assertThat(attributeAndTextBean.text).isEqualTo("nested text");

        assertThat(IntrospectionHelper.getHelper(ConfiguredNoArgBean.class)
                        .getElementType("noargchild"))
                .isEqualTo(NoArgChild.class);
        assertThat(IntrospectionHelper.getHelper(ConfiguredProjectBean.class)
                        .getElementType("projectchild"))
                .isEqualTo(ProjectChild.class);
        assertThat(IntrospectionHelper.getHelper(AddedNoArgBean.class).getElementType("addedchild"))
                .isEqualTo(NoArgChild.class);
        assertThat(IntrospectionHelper.getHelper(AddedProjectBean.class).getElementType("projectchild"))
                .isEqualTo(ProjectChild.class);
    }

    public static final class AttributeAndTextBean {
        private StringBackedValue value;
        private String text;

        public void setValue(StringBackedValue value) {
            this.value = value;
        }

        public void addText(String text) {
            this.text = text;
        }
    }

    public static final class StringBackedValue {
        private final String value;

        public StringBackedValue(String value) {
            this.value = value;
        }
    }

    public static final class ConfiguredNoArgBean {
        public void addConfiguredNoArgChild(NoArgChild child) {
        }
    }

    public static final class ConfiguredProjectBean {
        public void addConfiguredProjectChild(ProjectChild child) {
        }
    }

    public static final class AddedNoArgBean {
        public void addAddedChild(NoArgChild child) {
        }
    }

    public static final class AddedProjectBean {
        public void addProjectChild(ProjectChild child) {
        }
    }

    public static final class NoArgChild {
        public NoArgChild() {
        }
    }

    public static final class ProjectChild {
        public ProjectChild(Project project) {
        }
    }
}
