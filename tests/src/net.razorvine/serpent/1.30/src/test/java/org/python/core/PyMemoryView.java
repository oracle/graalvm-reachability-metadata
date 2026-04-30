/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

public final class PyMemoryView {
    private final byte[] bytes;

    public PyMemoryView(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public PyBytes tobytes() {
        return new PyBytes(bytes);
    }
}
