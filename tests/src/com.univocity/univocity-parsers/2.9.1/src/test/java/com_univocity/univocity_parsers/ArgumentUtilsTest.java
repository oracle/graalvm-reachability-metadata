/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.common.ArgumentUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArgumentUtilsTest {
    @Test
    public void findsDuplicatesWithArrayComponentTypePreserved() {
        final String[] values = {"name", "code", "name", "status", "code", "code"};

        final String[] duplicates = ArgumentUtils.findDuplicates(values);

        assertArrayEquals(new String[] {"name", "code", "code"}, duplicates);
        assertEquals(String[].class, duplicates.getClass());
    }
}
