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

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlAbstractParserImplInnerMetadataImplTest {
    @Test
    void derivesKeywordMetadataFromGeneratedParser() {
        SqlAbstractParserImpl parser = SqlParser.config()
                .parserFactory()
                .getParser(new StringReader("select 1"));
        SqlAbstractParserImpl.Metadata metadata =
                new SqlAbstractParserImpl.MetadataImpl(parser);

        assertThat(metadata.isKeyword("SELECT")).isTrue();
        assertThat(metadata.getTokens()).contains("SELECT");
        assertThat(metadata.getJdbcKeywords()).isNotBlank();
    }
}
