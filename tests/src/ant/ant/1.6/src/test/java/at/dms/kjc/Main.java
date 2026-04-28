/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at.dms.kjc;

public final class Main {
    private static String[] lastArguments;

    private Main() {
    }

    public static Boolean compile(String[] arguments) {
        lastArguments = (String[]) arguments.clone();
        return Boolean.TRUE;
    }

    public static void reset() {
        lastArguments = null;
    }

    public static String[] getLastArguments() {
        if (lastArguments == null) {
            return null;
        }
        return (String[]) lastArguments.clone();
    }
}
