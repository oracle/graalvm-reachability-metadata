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

public class IntrospectionHelperAnonymous6Test {
    @Test
    void setsCharacterAttributeThroughIntrospectionHelper() {
        Project project = new Project();
        CharacterAttributeElement element = new CharacterAttributeElement();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, CharacterAttributeElement.class);

        helper.setAttribute(project, element, "marker", "native");

        assertThat(element.marker).isEqualTo('n');
    }

    public static final class CharacterAttributeElement {
        private Character marker;

        public void setMarker(Character marker) {
            this.marker = marker;
        }
    }
}
