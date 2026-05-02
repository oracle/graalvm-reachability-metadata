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

public class IntrospectionHelperAnonymous5Test {
    @Test
    void configuresStringAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        StringAttributeElement parent = new StringAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                StringAttributeElement.class);

        helper.setAttribute(project, parent, "message", "configured text");

        assertThat(helper.getAttributeType("message")).isSameAs(String.class);
        assertThat(parent.message).isEqualTo("configured text");
    }

    public static class StringAttributeElement {
        private String message;

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
