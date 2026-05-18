/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interactivity_api;

import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PrompterAnonymous1Test {
    @Test
    void invokesPrompterContract() throws PrompterException {
        Prompter prompter = new Prompter() {
            @Override
            public String prompt(String message) {
                return "prompt:" + message;
            }

            @Override
            public String prompt(String message, String defaultReply) {
                return message + ":" + defaultReply;
            }

            @Override
            public String prompt(String message, List<String> possibleValues) {
                return possibleValues.get(0);
            }

            @Override
            public String prompt(String message, List<String> possibleValues, String defaultReply) {
                return possibleValues.contains(defaultReply) ? defaultReply : possibleValues.get(0);
            }

            @Override
            public String promptForPassword(String message) {
                return "secret-for-" + message;
            }

            @Override
            public void showMessage(String message) {
                assertThat(message).isEqualTo("visible");
            }
        };

        assertThat(prompter.prompt("name")).isEqualTo("prompt:name");
        assertThat(prompter.prompt("name", "plexus")).isEqualTo("name:plexus");
        assertThat(prompter.prompt("choice", List.of("one", "two"))).isEqualTo("one");
        assertThat(prompter.prompt("choice", List.of("one", "two"), "two")).isEqualTo("two");
        assertThat(prompter.promptForPassword("account")).isEqualTo("secret-for-account");
        prompter.showMessage("visible");
    }
}
