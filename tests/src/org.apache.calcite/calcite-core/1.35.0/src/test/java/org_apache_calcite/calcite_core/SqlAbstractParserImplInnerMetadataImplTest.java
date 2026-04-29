/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.calcite.sql.parser.SqlAbstractParserImpl;
import org.apache.calcite.sql.parser.SqlParser;
import org.junit.jupiter.api.Test;

public class SqlAbstractParserImplInnerMetadataImplTest {
    @Test
    void parserMetadataBuildsKeywordCatalogThroughParserApi() {
        SqlAbstractParserImpl.Metadata metadata = SqlParser.create("").getMetadata();

        List<String> tokens = metadata.getTokens();

        assertThat(tokens)
                .contains("SELECT", "FROM", "WHERE")
                .isSorted();
        assertThat(metadata.isKeyword("SELECT")).isTrue();
        assertThat(metadata.isReservedWord("SELECT")).isTrue();
        assertThat(metadata.isNonReservedKeyword("KEY")).isTrue();
        assertThat(metadata.isReservedFunctionName("COUNT")).isTrue();
        assertThat(metadata.isContextVariableName("CURRENT_DATE")).isTrue();
        assertThat(metadata.getJdbcKeywords()).contains("CURRENT_CATALOG");
    }
}
