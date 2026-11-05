/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.io.BinIO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class FastutilTestSupport {
    private FastutilTestSupport() {
    }

    enum Key {
        NORTH, EAST, SOUTH, WEST, CENTER, EXTRA, MISSING
    }

    enum Value {
        SHARED, EXTRA, MISSING
    }

    // Collisions ensure custom hash tables must use the strategy's equality operation.
    enum KeyStrategy implements Hash.Strategy<Key> {
        INSTANCE;

        @Override
        public int hashCode(Key key) {
            return 1;
        }

        @Override
        public boolean equals(Key first, Key second) {
            return first == second;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BinIO.storeObject(value, output);
            try (ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray())) {
                return (T) BinIO.loadObject(input);
            }
        }
    }
}
