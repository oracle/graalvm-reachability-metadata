/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

public class PyBytes {
    private final byte[] bytes;

    public PyBytes(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public byte[] toBytes() {
        return bytes.clone();
    }
}
