/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline.console;

import jline.console.completer.Completer;

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
    private Completer completer;

    public ConsoleReader() {
        lastInstance = this;
    }

    public static ConsoleReader lastInstance() {
        return lastInstance;
    }

    public void setBellEnabled(boolean bellEnabled) {
        this.bellEnabled = bellEnabled;
    }

    public void addCompleter(Completer completer) {
        this.completer = completer;
    }

    public String readLine() {
        return input.poll();
    }

    public String readLine(String prompt) {
        prompts.add(prompt);
        return input.poll();
    }

    public void flush() {
        flushed = true;
    }

    public void print(CharSequence value) {
        output.append(value);
    }

    public void println() {
        output.append(System.lineSeparator());
    }

    public void println(CharSequence value) {
        output.append(value).append(System.lineSeparator());
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

    public Completer getCompleter() {
        return completer;
    }

    public String getOutput() {
        return output.toString();
    }

    public List<String> getPrompts() {
        return prompts;
    }
}
