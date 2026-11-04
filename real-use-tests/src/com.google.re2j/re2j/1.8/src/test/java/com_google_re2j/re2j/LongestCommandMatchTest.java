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
 * Exercises the leftmost-longest matching subsystem to dispatch the most specific command in a request.
 */
public class LongestCommandMatchTest {

    @Test
    @Timeout(60)
    void dispatchesTheMostSpecificCommandAlias() {
        CommandDispatcher dispatcher = new CommandDispatcher();

        Command command = dispatcher.dispatch("deploy-canary service=payments");

        assertEquals("deploy-canary", command.name());
        assertEquals("service=payments", command.arguments());
    }

    /** Application command dispatch that prefers a longer registered command alias. */
    private static final class CommandDispatcher {

        private final Pattern commands = Pattern.compile(
                "deploy|deploy-canary|rollback", Pattern.LONGEST_MATCH);

        Command dispatch(String request) {
            Matcher matcher = commands.matcher(request);
            if (!matcher.find() || matcher.start() != 0) {
                throw new IllegalArgumentException("Unknown command: " + request);
            }
            return new Command(matcher.group(), request.substring(matcher.end()).trim());
        }
    }

    /** The command and arguments selected for execution by an application. */
    private record Command(String name, String arguments) {
    }
}
