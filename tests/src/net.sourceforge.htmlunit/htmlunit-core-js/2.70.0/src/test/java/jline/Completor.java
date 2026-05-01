/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jline;

import java.util.List;

public interface Completor {
    int complete(String buffer, int cursor, List<String> candidates);
}
