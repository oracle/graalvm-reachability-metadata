/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionHelperAnonymous13Test {
    @Test
    void createsRegisteredAntTypeAndAddsItToParentDuringCreation() {
        Project project = new Project();
        ComponentHelper.getComponentHelper(project)
                .addDataTypeDefinition("anonymous13-add-child", AddChild.class);
        AddParent parent = new AddParent();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, AddParent.class);

        Object child = helper.createElement(project, parent, "anonymous13-add-child");

        assertThat(child).isInstanceOf(AddChild.class);
        assertThat(parent.child).isSameAs(child);
    }

    @Test
    void storesRegisteredAntTypeWithAddConfiguredAfterCreation() {
        Project project = new Project();
        ComponentHelper.getComponentHelper(project)
                .addDataTypeDefinition("anonymous13-configured-child", ConfiguredChild.class);
        ConfiguredParent parent = new ConfiguredParent();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(
                project,
                ConfiguredParent.class);

        IntrospectionHelper.Creator creator = helper.getElementCreator(
                project,
                "",
                parent,
                "anonymous13-configured-child",
                null);
        Object child = creator.create();

        assertThat(child).isInstanceOf(ConfiguredChild.class);
        assertThat(parent.child).isNull();

        creator.store();

        assertThat(parent.child).isSameAs(child);
    }

    public static class AddParent {
        private AddChild child;

        public void add(AddChild child) {
            this.child = child;
        }
    }

    public static class ConfiguredParent {
        private ConfiguredChild child;

        public void addConfigured(ConfiguredChild child) {
            this.child = child;
        }
    }

    public static class AddChild {
        public AddChild() {
        }
    }

    public static class ConfiguredChild {
        public ConfiguredChild() {
        }
    }
}
