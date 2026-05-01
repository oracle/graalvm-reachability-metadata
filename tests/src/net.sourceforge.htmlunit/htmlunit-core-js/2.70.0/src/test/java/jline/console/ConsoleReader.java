/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.console;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import jline.console.completer.Completer;

public class ConsoleReader {
    private final Queue<String> lines;
    private boolean bellEnabled = true;

    public ConsoleReader() {
        lines = new ArrayDeque<>(List.of("v2-line", "v2-prompted", "v2-stream"));
    }

    public void setBellEnabled(boolean enabled) {
        bellEnabled = enabled;
    }

    public void addCompleter(Completer completer) {
        List<String> candidates = new ArrayList<>();
        int start = completer.complete("global.f", "global.f".length(), candidates);
        if (bellEnabled || start != "global.".length() || !candidates.contains("first")) {
            throw new AssertionError("Unexpected v2 completion result");
        }
    }

    public String readLine() {
        return lines.poll();
    }

    public String readLine(String prompt) {
        return lines.poll();
    }

    public void flush() {
    }

    public void print(CharSequence value) {
    }

    public void println() {
    }

    public void println(CharSequence value) {
    }
}
