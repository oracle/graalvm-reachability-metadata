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

public class IntrospectionHelperAnonymous12Test {
    @Test
    void convertsAttributeThroughStringConstructor() {
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper.getHelper(ConfigurationBean.class).setAttribute(new Project(), bean, "count", "42");
        assertEquals(42, bean.count.intValue());
    }
}
