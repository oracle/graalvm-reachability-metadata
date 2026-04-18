/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import javax.activation.CommandInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CommandInfoBeansTest {
    @Test
    void getCommandObjectInstantiatesBean() throws Exception {
        CommandInfo commandInfo = new CommandInfo("view", TestCommandBean.class.getName());

        Object commandObject = commandInfo.getCommandObject(null, CommandInfoBeansTest.class.getClassLoader());

        assertEquals(TestCommandBean.class.getName(), commandInfo.getCommandClass());
        assertInstanceOf(TestCommandBean.class, commandObject);
    }

    public static final class TestCommandBean {
        public TestCommandBean() {
        }
    }
}
