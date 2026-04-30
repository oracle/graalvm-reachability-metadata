/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

public final class PyByteArray {
    private final byte[] bytes;

    public PyByteArray(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public PyString __str__() {
        return new PyString(bytes);
    }
}
