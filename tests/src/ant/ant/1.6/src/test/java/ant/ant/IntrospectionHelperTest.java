/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

public class IntrospectionHelperTest {
    @Test
    void discoversConfigurationMethodsAndAddsText() {
        Project project = new Project();
        ConfigurationBean bean = new ConfigurationBean();
        IntrospectionHelper helper = IntrospectionHelper.getHelper(ConfigurationBean.class);
        assertTrue(helper.supportsCharacters());
        assertTrue(helper.supportsNestedElement("configured"));
        assertEquals(Integer.class, helper.getAttributeType("count"));
        helper.addText(project, bean, "nested text");
        assertEquals("nested text", bean.text);
    }
}
