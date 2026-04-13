/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import javax.activation.CommandInfo;
import javax.activation.CommandObject;
import javax.activation.DataHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public final class CommandInfoBeansTest {

    @Test
    void getCommandObjectInstantiatesBeanWithJavaBeansApi() throws Exception {
        DataHandler dataHandler = new DataHandler("payload", "text/plain");
        CommandInfo commandInfo = new CommandInfo("view", RecordingCommandBean.class.getName());

        Object commandObject = commandInfo.getCommandObject(dataHandler, RecordingCommandBean.class.getClassLoader());

        assertThat(commandObject).isInstanceOf(RecordingCommandBean.class);

        RecordingCommandBean bean = (RecordingCommandBean) commandObject;
        assertThat(bean.commandName).isEqualTo("view");
        assertThat(bean.dataHandler).isSameAs(dataHandler);
    }

    public static final class RecordingCommandBean implements CommandObject {
        private String commandName;
        private DataHandler dataHandler;

        public RecordingCommandBean() {
        }

        @Override
        public void setCommandContext(String verb, DataHandler dataHandler) {
            this.commandName = verb;
            this.dataHandler = dataHandler;
        }
    }
}
