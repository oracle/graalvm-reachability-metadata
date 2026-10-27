/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefineNamedBindingsStatementCustomizerInnerSetNullHandlerTest {
    @Test
    void definesWhetherNamedBindingsAreNull() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:defined_bindings;DB_CLOSE_DELAY=-1");

        String value = jdbi.withHandle(handle -> handle.createQuery("select :value")
                .bind("value", "defined")
                .defineNamedBindings()
                .mapTo(String.class)
                .one());

        assertThat(value).isEqualTo("defined");
    }
}
