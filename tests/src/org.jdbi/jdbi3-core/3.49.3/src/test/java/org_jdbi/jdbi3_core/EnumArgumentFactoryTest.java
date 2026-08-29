/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.enums.DatabaseValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumArgumentFactoryTest {
    @Test
    void bindsTheDatabaseValueDeclaredOnAnEnumConstant() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:enum_arguments;DB_CLOSE_DELAY=-1");

        String value = jdbi.withHandle(handle -> handle.createQuery("select :priority")
                .bind("priority", Priority.HIGH)
                .mapTo(String.class)
                .one());

        assertThat(value).isEqualTo("urgent");
    }

    public enum Priority {
        LOW,
        @DatabaseValue("urgent")
        HIGH
    }
}
