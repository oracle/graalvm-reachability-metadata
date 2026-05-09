/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_column;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import shaded.parquet.net.openhft.hashing.LongHashFunction;

public class UnsafeAccessTest {
    @Test
    void hashBooleanInitializesUnsafeAccess() {
        LongHashFunction hashFunction = LongHashFunction.xx();

        long trueHash = hashFunction.hashBoolean(true);
        long falseHash = hashFunction.hashBoolean(false);

        assertEquals(trueHash, hashFunction.hashBoolean(true));
        assertEquals(falseHash, hashFunction.hashBoolean(false));
        assertNotEquals(trueHash, falseHash);
    }
}
