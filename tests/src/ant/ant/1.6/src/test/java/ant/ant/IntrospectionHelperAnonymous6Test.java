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

public class IntrospectionHelperAnonymous6Test {
    @Test
    void configuresCharacterAttributeThroughPublicAntIntrospection() {
        Project project = new Project();
        CharacterAttributeElement parent = new CharacterAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                CharacterAttributeElement.class);

        helper.setAttribute(project, parent, "prefix", "sample");

        assertThat(helper.getAttributeType("prefix")).isSameAs(Character.TYPE);
        assertThat(parent.prefix).isEqualTo('s');
    }

    public static class CharacterAttributeElement {
        private char prefix;

        public void setPrefix(char prefix) {
            this.prefix = prefix;
        }
    }
}
