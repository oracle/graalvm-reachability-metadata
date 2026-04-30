/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

public final class PyTuple {
    private final Object[] values;

    public PyTuple(Object... values) {
        this.values = values;
    }

    public Object[] toArray() {
        return values;
    }
}
