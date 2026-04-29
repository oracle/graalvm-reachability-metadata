/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.runtime.CalciteResource;
import org.apache.calcite.runtime.Resources;
import org.apache.calcite.sql.parser.impl.ParseException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesInnerExInstWithCauseTest {
    @Test
    public void createsExceptionWithCauseConstructor() {
        CalciteResource resource = Resources.create(CalciteResource.class);
        IllegalArgumentException cause = new IllegalArgumentException("invalid SQL fragment");

        CalciteContextException exception = resource.validatorContextPoint(3, 15).ex(cause);

        assertThat(exception).hasMessageContaining("line 3, column 15");
        assertThat(exception).hasMessageContaining("invalid SQL fragment");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getPosLine()).isZero();
        assertThat(exception.getPosColumn()).isZero();
    }

    @Test
    public void createsExceptionWithMessageConstructorThenInitializesCause() {
        ParserResourceMessages messages = Resources.create(ParserResourceMessages.class);
        IllegalStateException cause = new IllegalStateException("parser stopped");

        ParseException exception = messages.parseFailed("select * from").ex(cause);

        assertThat(exception).hasMessage("Could not parse select * from");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    public interface ParserResourceMessages {
        @Resources.BaseMessage("Could not parse {0}")
        Resources.ExInstWithCause<ParseException> parseFailed(String sql);
    }
}
