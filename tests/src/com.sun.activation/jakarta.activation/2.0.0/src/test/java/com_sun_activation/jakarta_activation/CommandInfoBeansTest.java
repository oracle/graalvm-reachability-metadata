/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_activation.jakarta_activation;

import jakarta.activation.CommandInfo;
import jakarta.activation.CommandObject;
import jakarta.activation.DataHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandInfoBeansTest {
    @Test
    void getCommandObjectInstantiatesCommandObjectsViaJavaBeansInstantiate() throws Exception {
        CommandInfo commandInfo = new CommandInfo("view", RecordingCommandObject.class.getName());
        DataHandler dataHandler = new DataHandler("payload", "text/plain");

        Object commandObject = commandInfo.getCommandObject(dataHandler, RecordingCommandObject.class.getClassLoader());

        assertThat(commandObject).isInstanceOf(RecordingCommandObject.class);
        RecordingCommandObject bean = (RecordingCommandObject) commandObject;
        assertThat(bean.getVerb()).isEqualTo("view");
        assertThat(bean.getDataHandler()).isSameAs(dataHandler);
    }

    public static final class RecordingCommandObject implements CommandObject {
        private String verb;
        private DataHandler dataHandler;

        public RecordingCommandObject() {
        }

        @Override
        public void setCommandContext(String verb, DataHandler dataHandler) {
            this.verb = verb;
            this.dataHandler = dataHandler;
        }

        public String getVerb() {
            return verb;
        }

        public DataHandler getDataHandler() {
            return dataHandler;
        }
    }
}
