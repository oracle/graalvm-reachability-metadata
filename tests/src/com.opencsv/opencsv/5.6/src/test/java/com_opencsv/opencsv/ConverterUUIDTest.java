/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import java.io.StringReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ConverterUUIDTest {
    private static final UUID EXPECTED_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void parsesUuidFieldUsingAnnotatedBean() {
        List<UuidBean> beans = new CsvToBeanBuilder<UuidBean>(new StringReader("id\n" + EXPECTED_ID + "\n"))
                .withType(UuidBean.class)
                .build()
                .parse();

        assertThat(beans).hasSize(1);
        assertThat(beans.get(0).id).isEqualTo(EXPECTED_ID);
    }

    @Test
    void reportsInvalidUuidValueUsingAnnotatedBean() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> new CsvToBeanBuilder<UuidBean>(new StringReader("id\nnot-a-uuid\n"))
                        .withType(UuidBean.class)
                        .build()
                        .parse())
                .withCauseInstanceOf(CsvDataTypeMismatchException.class)
                .satisfies(exception -> assertThat(exception.getCause()).hasMessageContaining("not-a-uuid"));
    }

    public static class UuidBean {
        @CsvBindByName(column = "id")
        public UUID id;
    }
}
