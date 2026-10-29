/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous13Test {
    @Test
    void addsRegisteredPolymorphicElements() {
        Project project = new Project();
        project.addDataTypeDefinition("value", ConfigurationBean.Value.class);
        Immediate immediate = new Immediate();
        IntrospectionHelper.Creator creator = IntrospectionHelper.getHelper(Immediate.class)
                .getElementCreator(project, "", immediate, "value", null);
        Object value = creator.create();
        assertSame(value, immediate.value);
        creator.store();

        Deferred deferred = new Deferred();
        creator = IntrospectionHelper.getHelper(Deferred.class)
                .getElementCreator(project, "", deferred, "value", null);
        value = creator.create();
        assertNull(deferred.value);
        creator.store();
        assertSame(value, deferred.value);
    }

    public static class Immediate {
        ConfigurationBean.Value value;

        public void add(ConfigurationBean.Value value) {
            this.value = value;
        }
    }

    public static class Deferred {
        ConfigurationBean.Value value;

        public void addConfigured(ConfigurationBean.Value value) {
            this.value = value;
        }
    }
}
