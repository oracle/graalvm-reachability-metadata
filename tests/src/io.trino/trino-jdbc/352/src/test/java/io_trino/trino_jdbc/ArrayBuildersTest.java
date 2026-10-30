/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.util.ArrayBuilders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArrayBuildersTest {
    @Test
    void prependsAndPromotesWithoutDuplicates() {
        String[] original = {"second", "third"};
        String[] expanded = ArrayBuilders.insertInListNoDup(original, "first");
        assertArrayEquals(new String[] {"first", "second", "third"}, expanded);
        assertArrayEquals(new String[] {"third", "second"}, ArrayBuilders.insertInListNoDup(original, original[1]));
        assertSame(original, ArrayBuilders.insertInListNoDup(original, original[0]));
        assertArrayEquals(new String[] {"second", "third"}, original);
    }
}
