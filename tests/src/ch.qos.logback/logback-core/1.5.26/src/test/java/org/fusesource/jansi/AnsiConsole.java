/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.fusesource.jansi;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class AnsiConsole {
    private static final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private static final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    private static int systemInstallCount;
    private static int outCount;
    private static int wrapSystemErrCount;

    private AnsiConsole() {
    }

    public static void reset() {
        outBuffer.reset();
        errBuffer.reset();
        systemInstallCount = 0;
        outCount = 0;
        wrapSystemErrCount = 0;
    }

    public static void systemInstall() {
        systemInstallCount++;
    }

    public static PrintStream out() {
        outCount++;
        return new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
    }

    public static OutputStream wrapSystemErr(PrintStream ignored) {
        wrapSystemErrCount++;
        return new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
    }

    public static int systemInstallCount() {
        return systemInstallCount;
    }

    public static int outCount() {
        return outCount;
    }

    public static int wrapSystemErrCount() {
        return wrapSystemErrCount;
    }

    public static String outContent() {
        return outBuffer.toString(StandardCharsets.UTF_8);
    }

    public static String errContent() {
        return errBuffer.toString(StandardCharsets.UTF_8);
    }
}
