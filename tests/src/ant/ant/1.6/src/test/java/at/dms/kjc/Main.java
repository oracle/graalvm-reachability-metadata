/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at.dms.kjc;

import java.util.Arrays;

public class Main {
    private static int invocationCount;
    private static String[] arguments;

    public static Boolean compile(String[] arguments) {
        invocationCount++;
        Main.arguments = Arrays.copyOf(arguments, arguments.length);
        return Boolean.TRUE;
    }

    public static void reset() {
        invocationCount = 0;
        arguments = new String[0];
    }

    public static int invocationCount() {
        return invocationCount;
    }

    public static String[] lastArguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }
}
