/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous13Test {
    @Test
    void addsTypedComponentWhenCreatorCreatesNestedElement() {
        Project project = newProject();
        project.addDataTypeDefinition("recorded-child", RecordedChild.class);
        AddParent parent = new AddParent();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, AddParent.class);

        IntrospectionHelper.Creator creator = helper.getElementCreator(project, "", parent, "recorded-child", null);
        Object created = creator.create();
        creator.store();

        assertThat(created).isInstanceOf(RecordedChild.class);
        assertThat(parent.children).containsExactly(created);
    }

    @Test
    void addsConfiguredTypedComponentWhenCreatorStoresNestedElement() {
        Project project = newProject();
        project.addDataTypeDefinition("configured-recorded-child", RecordedChild.class);
        AddConfiguredParent parent = new AddConfiguredParent();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(project, AddConfiguredParent.class);

        IntrospectionHelper.Creator creator = helper.getElementCreator(
                project, "", parent, "configured-recorded-child", null);
        Object created = creator.create();

        assertThat(created).isInstanceOf(RecordedChild.class);
        assertThat(parent.children).isEmpty();

        creator.store();

        assertThat(parent.children).containsExactly(created);
    }

    private static Project newProject() {
        Project project = new Project();
        project.init();
        return project;
    }

    public static final class AddParent {
        private final List<RecordedChild> children = new ArrayList<>();

        public void add(RecordedChild child) {
            children.add(child);
        }
    }

    public static final class AddConfiguredParent {
        private final List<RecordedChild> children = new ArrayList<>();

        public void addConfigured(RecordedChild child) {
            children.add(child);
        }
    }

    public static final class RecordedChild {
        public RecordedChild() {
        }
    }
}
