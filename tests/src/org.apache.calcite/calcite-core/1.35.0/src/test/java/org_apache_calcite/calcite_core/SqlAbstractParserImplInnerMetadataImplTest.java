/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.parser.SqlAbstractParserImpl;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.parser.impl.SqlParserImpl;
import org.apache.calcite.sql.validate.SqlConformance;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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

    @Test
    public void invokesMetadataProductionBeforeRejectingSuccessfulParse() {
        Throwable thrown = catchThrowable(() -> new SqlAbstractParserImpl.MetadataImpl(
                new SuccessfulInvocationParser()));

        assertThat(thrown)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("While building token lists");
        assertThat(thrown.getCause())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("reserved function");
    }

    public static final class SuccessfulInvocationParser extends SqlAbstractParserImpl {
        // Checkstyle: stop method name check
        public String ReservedFunctionName() {
            return "reserved function";
        }

        public String ContextVariable() {
            return "context variable";
        }

        public String NonReservedKeyWord() {
            return "non-reserved keyword";
        }
        // Checkstyle: resume method name check

        @Override
        public Metadata getMetadata() {
            throw unsupported();
        }

        @Override
        public SqlParseException normalizeException(Throwable ex) {
            throw unsupported();
        }

        @Override
        protected SqlParserPos getPos() {
            throw unsupported();
        }

        @Override
        public void ReInit(Reader reader) {
            // Keep the parser in the state that lets the reflected production return successfully.
        }

        @Override
        public SqlNode parseSqlExpressionEof() {
            throw unsupported();
        }

        @Override
        public SqlNode parseSqlStmtEof() {
            throw unsupported();
        }

        @Override
        public SqlNodeList parseSqlStmtList() {
            throw unsupported();
        }

        @Override
        public void setTabSize(int tabSize) {
            throw unsupported();
        }

        @Override
        public void setQuotedCasing(Casing quotedCasing) {
            throw unsupported();
        }

        @Override
        public void setUnquotedCasing(Casing unquotedCasing) {
            throw unsupported();
        }

        @Override
        public void setIdentifierMaxLength(int identifierMaxLength) {
            throw unsupported();
        }

        @Override
        public void setConformance(SqlConformance conformance) {
            throw unsupported();
        }

        @Override
        public SqlNode parseArray() {
            throw unsupported();
        }

        @Override
        public void switchTo(LexicalState state) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Not used by metadata initialization test");
        }
    }
}
