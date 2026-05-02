/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package weblogic;

import java.util.Arrays;

@SuppressWarnings("checkstyle:TypeName")
public class rmic {
    private static int invocationCount;
    private static String[] arguments = new String[0];

    public static void main(String[] arguments) {
        invocationCount++;
        rmic.arguments = Arrays.copyOf(arguments, arguments.length);
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
