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
    private static ConsoleReader lastInstance;

    private final Queue<String> input = new ArrayDeque<>();
    private final List<String> prompts = new ArrayList<>();
    private final StringBuilder output = new StringBuilder();
    private boolean bellEnabled = true;
    private boolean flushed;
    private Completor completor;

    public ConsoleReader() {
        lastInstance = this;
    }

    public static ConsoleReader lastInstance() {
        return lastInstance;
    }

    public void setBellEnabled(boolean bellEnabled) {
        this.bellEnabled = bellEnabled;
    }

    public void addCompletor(Completor completor) {
        this.completor = completor;
    }

    public String readLine() {
        return input.poll();
    }

    public String readLine(String prompt) {
        prompts.add(prompt);
        return input.poll();
    }

    public void flushConsole() {
        flushed = true;
    }

    public void printString(String value) {
        output.append(value);
    }

    public void printNewline() {
        output.append(System.lineSeparator());
    }

    public void addInput(String line) {
        input.add(line);
    }

    public boolean isBellEnabled() {
        return bellEnabled;
    }

    public boolean isFlushed() {
        return flushed;
    }

    public Completor getCompletor() {
        return completor;
    }

    public String getOutput() {
        return output.toString();
    }

    public List<String> getPrompts() {
        return prompts;
    }
}
