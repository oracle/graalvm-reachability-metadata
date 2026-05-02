/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionHelperAnonymous11Test {
    @Test
    void configuresEnumeratedAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        EnumeratedAttributeElement parent = new EnumeratedAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                EnumeratedAttributeElement.class);

        helper.setAttribute(project, parent, "mode", "strict");

        assertThat(helper.getAttributeType("mode")).isSameAs(ModeAttribute.class);
        assertThat(parent.mode).isInstanceOf(ModeAttribute.class);
        assertThat(parent.mode.getValue()).isEqualTo("strict");
    }

    public static class EnumeratedAttributeElement {
        private ModeAttribute mode;

        public void setMode(ModeAttribute mode) {
            this.mode = mode;
        }
    }

    public static class ModeAttribute extends EnumeratedAttribute {
        public ModeAttribute() {
        }

        @Override
        public String[] getValues() {
            return new String[] {"strict", "lenient"};
        }
    }
}
