/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kaffe.rmi.rmic;

public class RMIC {
    private static String[] lastArguments = new String[0];

    private final String[] arguments;

    public RMIC(String[] arguments) {
        this.arguments = arguments.clone();
        lastArguments = this.arguments.clone();
    }

    public Boolean run() {
        lastArguments = arguments.clone();
        return Boolean.TRUE;
    }

    public static String[] lastArguments() {
        return lastArguments.clone();
    }
}
