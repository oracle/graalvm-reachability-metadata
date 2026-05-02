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

public class IntrospectionHelperAnonymous8Test {
    @Test
    void configuresClassAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        ClassAttributeElement parent = new ClassAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                ClassAttributeElement.class);

        helper.setAttribute(project, parent, "implementation", "java.lang.String");

        assertThat(helper.getAttributeType("implementation")).isSameAs(Class.class);
        assertThat(parent.implementation).isSameAs(String.class);
    }

    public static class ClassAttributeElement {
        private Class implementation;

        public void setImplementation(Class implementation) {
            this.implementation = implementation;
        }
    }
}
