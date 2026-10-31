/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.ConverterUUID;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class ConverterUUIDTest {
    @Test
    void readsTrimmedUuidsAndRejectsMalformedIdentifiers() throws Exception {
        ConverterUUID converter = new ConverterUUID(Locale.US);
        String identifier = "ed6d42bc-c8cf-4ada-9236-cd02ae63b2e7";
        assertThat(converter.convertToRead("  " + identifier + "  ")).isEqualTo(UUID.fromString(identifier));
        assertThatThrownBy(() -> converter.convertToRead("not-a-uuid"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("not-a-uuid")
                .hasMessageContaining("UUID");
    }
}
