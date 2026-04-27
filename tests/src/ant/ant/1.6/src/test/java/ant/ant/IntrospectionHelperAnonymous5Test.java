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

public class IntrospectionHelperAnonymous5Test {
    @Test
    void setsStringAttributeThroughIntrospectionHelper() {
        Project project = new Project();
        StringAttributeElement element = new StringAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, StringAttributeElement.class);

        helper.setAttribute(project, element, "message", "configured through introspection");

        assertThat(element.message).isEqualTo("configured through introspection");
    }

    public static final class StringAttributeElement {
        private String message;

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
