/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the incremental replacement subsystem to render user mentions in a chat message.
 */
public class MentionRenderingTest {

    @Test
    @Timeout(60)
    void rendersMentionsWhilePreservingTheMessageAroundThem() {
        MessageRenderer renderer = new MessageRenderer();

        String rendered = renderer.render("Deploy update for @alice and @team-ops is ready.");

        assertEquals(
                "Deploy update for <mention>alice</mention> and <mention>team-ops</mention> is ready.",
                rendered);
    }

    /** Application renderer that turns recognized mentions into dashboard markup. */
    private static final class MessageRenderer {

        private final Pattern mention = Pattern.compile("@(?<name>[a-z][a-z0-9-]*)");

        String render(String message) {
            Matcher matcher = mention.matcher(message);
            StringBuilder rendered = new StringBuilder();
            while (matcher.find()) {
                String replacement = "<mention>" + matcher.group("name") + "</mention>";
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(rendered);
            return rendered.toString();
        }
    }
}
