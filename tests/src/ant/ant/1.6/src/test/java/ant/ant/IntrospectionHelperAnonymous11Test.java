/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperAnonymous11Test {
    @Test
    void convertsEnumeratedAttribute() {
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper.getHelper(ConfigurationBean.class).setAttribute(new Project(), bean, "mode", "careful");
        assertEquals("careful", bean.mode.getValue());
    }
}
