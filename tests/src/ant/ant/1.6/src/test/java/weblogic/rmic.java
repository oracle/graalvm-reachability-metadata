/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package weblogic;

import java.util.Arrays;

// CheckStyle: start generated
public class rmic {
    private static String[] lastArguments = new String[0];

    public static void main(String[] arguments) {
        lastArguments = Arrays.copyOf(arguments, arguments.length);
    }

    public static void reset() {
        lastArguments = new String[0];
    }

    public static String[] getLastArguments() {
        return Arrays.copyOf(lastArguments, lastArguments.length);
    }
}
// CheckStyle: stop generated
