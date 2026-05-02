/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kaffe.rmi.rmic;

public class RMIC {
    private final String[] arguments;

    public RMIC(String[] arguments) {
        this.arguments = arguments;
    }

    public Boolean run() {
        return Boolean.valueOf(arguments.length >= 0);
    }
}
