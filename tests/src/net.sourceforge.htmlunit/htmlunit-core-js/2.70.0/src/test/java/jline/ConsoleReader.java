/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class ConsoleReader {
    private final Queue<String> lines;
    private boolean bellEnabled = true;

    public ConsoleReader() {
        lines = new ArrayDeque<>(List.of("v1-line", "v1-prompted", "v1-stream"));
    }

    public void setBellEnabled(boolean enabled) {
        bellEnabled = enabled;
    }

    public void addCompletor(Completor completor) {
        List<String> candidates = new ArrayList<>();
        int start = completor.complete("global.f", "global.f".length(), candidates);
        if (bellEnabled || start != "global.".length() || !candidates.contains("first")) {
            throw new AssertionError("Unexpected v1 completion result");
        }
    }

    public String readLine() {
        return lines.poll();
    }

    public String readLine(String prompt) {
        return lines.poll();
    }

    public void flushConsole() {
    }

    public void printString(String value) {
    }

    public void printNewline() {
    }
}
