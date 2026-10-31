/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReaderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CSVReaderBuilderTest {
    @Test
    void rejectsNullInputReader() {
        assertThatThrownBy(() -> new CSVReaderBuilder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reader");
    }
}
