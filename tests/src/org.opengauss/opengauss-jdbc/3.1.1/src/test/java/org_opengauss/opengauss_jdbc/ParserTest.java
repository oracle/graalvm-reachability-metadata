/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.core.Parser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.core.Parser}.
 */
public class ParserTest {

    @Test
    void replaceProcessingInvokesRegisteredEscapedFunctionTranslator() throws Exception {
        String sql = "select {fn concat({fn lcase('Hello')}, {fn ucase('world')})}";

        String replacedSql = Parser.replaceProcessing(sql, true, true);

        assertThat(replacedSql).isEqualTo("select (lower('Hello')|| upper('world'))");
    }
}
