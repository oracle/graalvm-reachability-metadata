/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencsv.CSVReaderBuilder;
import org.junit.jupiter.api.Test;

public class CSVReaderBuilderTest {
    @Test
    void constructorRejectsNullReader() {
        assertThrows(IllegalArgumentException.class,
                () -> new CSVReaderBuilder(null));
    }
}
