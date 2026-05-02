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

public class IntrospectionHelperAnonymous1Test {
    @Test
    void createsNestedElementThroughCreateMethod() {
        Project project = new Project();
        CreateMethodParent parent = new CreateMethodParent();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                CreateMethodParent.class);

        Object child = helper.createElement(project, parent, "generated");

        assertThat(child).isInstanceOf(GeneratedChild.class);
        assertThat(((GeneratedChild) child).value).isEqualTo("created by parent");
        assertThat(parent.generatedChild).isSameAs(child);
        assertThat(helper.getElementType("generated")).isSameAs(GeneratedChild.class);
    }

    public static class CreateMethodParent {
        private GeneratedChild generatedChild;

        public GeneratedChild createGenerated() {
            this.generatedChild = new GeneratedChild("created by parent");
            return this.generatedChild;
        }
    }

    public static class GeneratedChild {
        private final String value;

        public GeneratedChild(String value) {
            this.value = value;
        }
    }
}
