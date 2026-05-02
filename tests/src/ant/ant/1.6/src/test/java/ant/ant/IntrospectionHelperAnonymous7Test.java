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

public class IntrospectionHelperAnonymous7Test {
    @Test
    void configuresBooleanAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        BooleanAttributeElement parent = new BooleanAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                BooleanAttributeElement.class);

        helper.setAttribute(project, parent, "enabled", "yes");

        assertThat(helper.getAttributeType("enabled")).isSameAs(Boolean.TYPE);
        assertThat(parent.enabled).isTrue();
    }

    public static class BooleanAttributeElement {
        private boolean enabled;

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
