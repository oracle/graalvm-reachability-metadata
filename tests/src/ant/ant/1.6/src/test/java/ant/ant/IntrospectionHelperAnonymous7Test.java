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

public class IntrospectionHelperAnonymous7Test {
    @Test
    void setsBooleanAttributeThroughIntrospectionHelper() {
        Project project = new Project();
        BooleanAttributeElement element = new BooleanAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, BooleanAttributeElement.class);

        helper.setAttribute(project, element, "enabled", "true");

        assertThat(element.enabled).isTrue();
    }

    public static final class BooleanAttributeElement {
        private Boolean enabled;

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
