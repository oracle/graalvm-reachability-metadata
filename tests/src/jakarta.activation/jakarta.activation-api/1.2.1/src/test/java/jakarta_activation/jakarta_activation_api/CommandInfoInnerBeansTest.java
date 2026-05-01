/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import javax.activation.CommandInfo;
import javax.activation.CommandObject;
import javax.activation.DataHandler;

import org.junit.jupiter.api.Test;

public class CommandInfoInnerBeansTest {

    @Test
    void getCommandObjectInstantiatesBeanThroughJavaBeans() throws Exception {
        CommandInfo commandInfo = new CommandInfo("view", TestCommandBean.class.getName());

        Object commandObject = commandInfo.getCommandObject(null, getClass().getClassLoader());

        assertThat(commandObject).isInstanceOf(TestCommandBean.class);
        TestCommandBean bean = (TestCommandBean) commandObject;
        assertThat(bean.commandName).isEqualTo("view");
        assertThat(bean.dataHandler).isNull();
    }

    public static final class TestCommandBean implements CommandObject {

        private String commandName;
        private DataHandler dataHandler;

        public TestCommandBean() {
        }

        @Override
        public void setCommandContext(String verb, DataHandler dh) throws IOException {
            this.commandName = verb;
            this.dataHandler = dh;
        }
    }
}
