/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseFormat;
import org.junit.jupiter.api.Test;

public class ClickHouseFormatTest {
    @Test
    void derivesDefaultInputFormatForOutputOnlyTextFormat() {
        assertThat(ClickHouseFormat.Markdown.supportsInput()).isFalse();
        assertThat(ClickHouseFormat.Markdown.isText()).isTrue();
        assertThat(ClickHouseFormat.Markdown.defaultInputFormat()).isEqualTo(ClickHouseFormat.TabSeparated);
    }

    @Test
    void derivesDefaultInputFormatForOutputOnlyBinaryFormat() {
        assertThat(ClickHouseFormat.MySQLWire.supportsInput()).isFalse();
        assertThat(ClickHouseFormat.MySQLWire.isBinary()).isTrue();
        assertThat(ClickHouseFormat.MySQLWire.defaultInputFormat()).isEqualTo(ClickHouseFormat.RowBinary);
    }
}
