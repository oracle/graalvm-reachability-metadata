/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.fusesource.jansi;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class AnsiConsole {
    private static int systemInstallCalls;
    private static int outCalls;
    private static int wrapSystemErrCalls;
    private static ByteArrayOutputStream outBuffer;
    private static ByteArrayOutputStream wrappedOutBuffer;
    private static ByteArrayOutputStream wrappedErrBuffer;
    private static PrintStream outStream;
    private static PrintStream wrappedOutStream;
    private static PrintStream wrappedErrStream;

    static {
        reset();
    }

    private AnsiConsole() {
    }

    public static void reset() {
        systemInstallCalls = 0;
        outCalls = 0;
        wrapSystemErrCalls = 0;
        outBuffer = new ByteArrayOutputStream();
        wrappedOutBuffer = new ByteArrayOutputStream();
        wrappedErrBuffer = new ByteArrayOutputStream();
        outStream = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
        wrappedOutStream = new PrintStream(wrappedOutBuffer, true, StandardCharsets.UTF_8);
        wrappedErrStream = new PrintStream(wrappedErrBuffer, true, StandardCharsets.UTF_8);
    }

    public static void systemInstall() {
        systemInstallCalls++;
    }

    public static PrintStream out() {
        outCalls++;
        return outStream;
    }

    public static PrintStream wrapSystemOut(PrintStream target) {
        Objects.requireNonNull(target, "target");
        return wrappedOutStream;
    }

    public static PrintStream wrapSystemErr(PrintStream target) {
        Objects.requireNonNull(target, "target");
        wrapSystemErrCalls++;
        return wrappedErrStream;
    }

    public static int systemInstallCalls() {
        return systemInstallCalls;
    }

    public static int outCalls() {
        return outCalls;
    }

    public static int wrapSystemErrCalls() {
        return wrapSystemErrCalls;
    }

    public static PrintStream outStream() {
        return outStream;
    }

    public static PrintStream wrappedErrStream() {
        return wrappedErrStream;
    }

    public static String outContents() {
        return outBuffer.toString(StandardCharsets.UTF_8);
    }

    public static String wrappedErrContents() {
        return wrappedErrBuffer.toString(StandardCharsets.UTF_8);
    }
}
