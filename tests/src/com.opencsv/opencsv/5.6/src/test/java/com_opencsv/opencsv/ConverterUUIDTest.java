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

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConverterUUIDTest {
    @Test
    void convertsUuidValue() throws CsvDataTypeMismatchException {
        ConverterUUID converter = new ConverterUUID(Locale.US);

        Object converted = converter.convertToRead(" 123e4567-e89b-12d3-a456-426614174000 ");

        assertThat(converted).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void loadsErrorBundleWhenUuidValueIsInvalid() {
        ConverterUUID converter = new ConverterUUID(Locale.US);

        assertThatThrownBy(() -> converter.convertToRead("not-a-uuid"))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("not-a-uuid")
                .hasMessageContaining("valid pattern for UUID");
    }
}
