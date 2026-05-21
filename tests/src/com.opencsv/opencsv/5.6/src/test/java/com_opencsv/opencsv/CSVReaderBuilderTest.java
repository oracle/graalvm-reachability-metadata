/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReaderBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class CSVReaderBuilderTest {
    @Test
    void constructorRejectsNullReaderWithLocalizedMessage() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CSVReaderBuilder(null))
                .withMessage("The Reader must always be non-null.");
    }
}
