/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.sql.parser.SqlAbstractParserImpl;
import org.apache.calcite.sql.parser.impl.SqlParserImpl;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlAbstractParserImplInnerMetadataImplTest {
    @Test
    public void buildsMetadataFromParserKeywordProductions() {
        SqlAbstractParserImpl.Metadata metadata = new SqlAbstractParserImpl.MetadataImpl(
                new SqlParserImpl(new StringReader("")));

        assertThat(metadata.getTokens())
                .contains("A", "ABS", "CURRENT_USER", "SELECT");
        assertThat(metadata.isNonReservedKeyword("A")).isTrue();
        assertThat(metadata.isReservedFunctionName("ABS")).isTrue();
        assertThat(metadata.isContextVariableName("CURRENT_USER")).isTrue();
        assertThat(metadata.isReservedWord("SELECT")).isTrue();
        assertThat(metadata.isKeyword("CURRENT_USER")).isTrue();
    }
}
