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

public class IntrospectionHelperAnonymous2Test {
    @Test
    void storesConfiguredChildrenAfterCreation() {
        Project project = new Project();
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(ConfigurationBean.class);
        Object child = helper.createElement(project, bean, "configured");
        assertNull(bean.configured);
        helper.storeElement(project, bean, child, "configured");
        assertSame(child, bean.configured);
        Object projectChild = helper.createElement(project, bean, "projectconfigured");
        helper.storeElement(project, bean, projectChild, "projectconfigured");
        assertSame(projectChild, bean.projectConfigured);
        assertSame(project, bean.projectConfigured.project);
    }
}
