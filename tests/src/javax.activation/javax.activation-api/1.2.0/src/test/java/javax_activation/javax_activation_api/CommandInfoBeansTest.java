/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import java.io.IOException;

import javax.activation.CommandInfo;
import javax.activation.CommandObject;
import javax.activation.DataHandler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandInfoBeansTest {
    @Test
    void getCommandObjectInstantiatesCommandObjectThroughBeansApi() throws Exception {
        DataHandler dataHandler = new DataHandler("payload", "text/plain");
        CommandInfo commandInfo = new CommandInfo("view", RecordingCommandObject.class.getName());

        Object commandObject = commandInfo.getCommandObject(dataHandler, RecordingCommandObject.class.getClassLoader());

        assertThat(commandObject).isInstanceOf(RecordingCommandObject.class);
        RecordingCommandObject recordingCommandObject = (RecordingCommandObject) commandObject;
        assertThat(recordingCommandObject.getVerb()).isEqualTo("view");
        assertThat(recordingCommandObject.getDataHandler()).isSameAs(dataHandler);
    }

    public static final class RecordingCommandObject implements CommandObject {
        private String verb;
        private DataHandler dataHandler;

        public RecordingCommandObject() {
        }

        @Override
        public void setCommandContext(String verb, DataHandler dataHandler) throws IOException {
            this.verb = verb;
            this.dataHandler = dataHandler;
        }

        String getVerb() {
            return verb;
        }

        DataHandler getDataHandler() {
            return dataHandler;
        }
    }
}
