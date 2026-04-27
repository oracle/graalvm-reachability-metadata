/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.tools.ant.types.selectors.cacheselector;

import java.util.Comparator;

public class EqualComparator implements Comparator<Object> {
    public EqualComparator() {
    }

    @Override
    public int compare(Object left, Object right) {
        return String.valueOf(left).equals(String.valueOf(right)) ? 0 : 1;
    }
}
