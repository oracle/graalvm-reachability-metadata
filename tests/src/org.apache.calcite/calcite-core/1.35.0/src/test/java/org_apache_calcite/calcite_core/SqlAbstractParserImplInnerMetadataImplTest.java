/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.sql.parser.SqlAbstractParserImpl;
import org.apache.calcite.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlAbstractParserImplInnerMetadataImplTest {
    @Test
    void derivesKeywordMetadataFromGeneratedParser() {
        SqlAbstractParserImpl.Metadata metadata = SqlParser.create("select 1").getMetadata();

        assertThat(metadata.isKeyword("SELECT")).isTrue();
        assertThat(metadata.getTokens()).contains("SELECT");
        assertThat(metadata.getJdbcKeywords()).isNotBlank();
    }
}
