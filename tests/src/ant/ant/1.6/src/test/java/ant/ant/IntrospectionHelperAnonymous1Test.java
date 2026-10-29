/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous1Test {
    @Test
    void createsNestedElementUsingFactoryMethod() {
        Project project = new Project();
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(ConfigurationBean.class);
        ConfigurationBean.Value child = (ConfigurationBean.Value) helper.createElement(project, bean, "created");
        IntrospectionHelper.getHelper(ConfigurationBean.Value.class).setAttribute(project, child, "label", "child");
        assertSame(child, bean.created);
        assertEquals("child", bean.created.label);
    }
}
