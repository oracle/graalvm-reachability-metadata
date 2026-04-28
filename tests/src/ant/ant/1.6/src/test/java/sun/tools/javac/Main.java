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
    private static String lastProgramName;
    private static String[] lastArguments;

    public Main(OutputStream outputStream, String programName) {
        lastProgramName = programName;
    }

    public boolean compile(String[] arguments) {
        lastArguments = Arrays.copyOf(arguments, arguments.length);
        return true;
    }

    public static void reset() {
        lastProgramName = null;
        lastArguments = null;
    }

    public static String getLastProgramName() {
        return lastProgramName;
    }

    public static String[] getLastArguments() {
        return Arrays.copyOf(lastArguments, lastArguments.length);
    }
}
