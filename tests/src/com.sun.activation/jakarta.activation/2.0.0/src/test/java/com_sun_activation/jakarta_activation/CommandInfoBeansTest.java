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

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandInfoBeansTest {

    @Test
    void getCommandObjectInstantiatesCommandBeanWithJavaBeansSupport() throws IOException, ClassNotFoundException {
        String verb = "view";
        DataHandler dataHandler = new DataHandler("payload", "text/plain");
        CommandInfo commandInfo = new CommandInfo(verb, RecordingCommandBean.class.getName());

        Object commandObject = commandInfo.getCommandObject(dataHandler, CommandInfoBeansTest.class.getClassLoader());

        assertThat(commandObject).isInstanceOf(RecordingCommandBean.class);

        RecordingCommandBean bean = (RecordingCommandBean) commandObject;
        assertThat(bean.contextSet).isTrue();
        assertThat(bean.verb).isEqualTo(verb);
        assertThat(bean.dataHandler).isSameAs(dataHandler);
    }

    public static final class RecordingCommandBean implements CommandObject {

        private boolean contextSet;
        private String verb;
        private DataHandler dataHandler;

        public RecordingCommandBean() {
        }

        @Override
        public void setCommandContext(String verb, DataHandler dataHandler) throws IOException {
            this.contextSet = true;
            this.verb = verb;
            this.dataHandler = dataHandler;
        }
    }
}
