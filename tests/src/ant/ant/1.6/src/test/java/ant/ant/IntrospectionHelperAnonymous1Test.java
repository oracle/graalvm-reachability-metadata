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

public class IntrospectionHelperAnonymous1Test {
    @Test
    void createsNestedElementByInvokingParentCreateMethod() {
        Project project = new Project();
        CreateParent parent = new CreateParent();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, CreateParent.class);

        Object nestedElement = helper.createElement(project, parent, "createdchild");

        assertThat(nestedElement).isInstanceOf(CreatedChild.class);
        CreatedChild createdChild = (CreatedChild) nestedElement;
        assertThat(createdChild).isSameAs(parent.createdChild);
        assertThat(createdChild.value).isEqualTo("created by parent");
    }

    public static final class CreateParent {
        private CreatedChild createdChild;

        public CreatedChild createCreatedChild() {
            createdChild = new CreatedChild("created by parent");
            return createdChild;
        }
    }

    public static final class CreatedChild {
        private final String value;

        public CreatedChild(String value) {
            this.value = value;
        }
    }
}
