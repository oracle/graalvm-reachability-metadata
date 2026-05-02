/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package sun.tools.javac;

import java.io.OutputStream;
import java.util.Arrays;

public class Main {
    private static int invocationCount;
    private static String compilerName;
    private static String[] arguments;

    private final String name;

    public Main(OutputStream outputStream, String name) {
        this.name = name;
    }

    public Boolean compile(String[] arguments) {
        invocationCount++;
        compilerName = name;
        Main.arguments = Arrays.copyOf(arguments, arguments.length);
        return Boolean.TRUE;
    }

    public static void reset() {
        invocationCount = 0;
        compilerName = null;
        arguments = new String[0];
    }

    public static int invocationCount() {
        return invocationCount;
    }

    public static String lastCompilerName() {
        return compilerName;
    }

    public static String[] lastArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }
}
