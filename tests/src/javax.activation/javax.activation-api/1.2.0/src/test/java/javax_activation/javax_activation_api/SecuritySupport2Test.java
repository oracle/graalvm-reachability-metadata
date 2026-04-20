/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import javax.activation.CommandInfo;
import javax.activation.MailcapCommandMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupport2Test {
    @Test
    void constructorLoadsMailcapDefaultFromClasspathResource() {
        MailcapCommandMap commandMap = new MailcapCommandMap();

        CommandInfo[] commands = commandMap.getPreferredCommands("text/x-security-support-2");

        assertThat(commands).singleElement().satisfies(command -> {
            assertThat(command.getCommandName()).isEqualTo("view");
            assertThat(command.getCommandClass()).isEqualTo(MailcapViewCommand.class.getName());
        });
    }

    public static final class MailcapViewCommand {
        public MailcapViewCommand() {
        }
    }
}
