/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous3Test {
    @Test
    void addsChildrenDuringCreation() {
        Project project = new Project();
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(ConfigurationBean.class);
        Object child = helper.createElement(project, bean, "added");
        assertSame(child, bean.added);
        Object projectChild = helper.createElement(project, bean, "projectadded");
        assertSame(projectChild, bean.projectAdded);
        assertSame(project, bean.projectAdded.project);
    }
}
