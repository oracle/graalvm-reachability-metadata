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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PrompterAnonymous1Test {
    @Test
    void promptsForRepliesAndShowsMessages() throws PrompterException {
        List<String> shownMessages = new ArrayList<>();
        Prompter prompter = new Prompter() {
            @Override
            public String prompt(String message) {
                return "reply to " + message;
            }

            @Override
            public String prompt(String message, String defaultReply) {
                return defaultReply;
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
                return "secret for " + message;
            }

            @Override
            public void showMessage(String message) {
                shownMessages.add(message);
            }
        };

        assertThat(prompter.prompt("name")).isEqualTo("reply to name");
        assertThat(prompter.prompt("name", "guest")).isEqualTo("guest");
        assertThat(prompter.prompt("color", List.of("red", "blue"))).isEqualTo("red");
        assertThat(prompter.prompt("color", List.of("red", "blue"), "blue")).isEqualTo("blue");
        assertThat(prompter.promptForPassword("database")).isEqualTo("secret for database");

        prompter.showMessage("ready");

        assertThat(shownMessages).containsExactly("ready");
    }
}
