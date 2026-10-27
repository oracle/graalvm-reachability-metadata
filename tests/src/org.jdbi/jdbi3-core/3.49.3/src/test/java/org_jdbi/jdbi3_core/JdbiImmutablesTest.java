/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbiImmutablesTest {
    @Test
    void locatesConventionalImmutableAndModifiableImplementations() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:jdbi_immutables;DB_CLOSE_DELAY=-1");
        JdbiImmutables immutables = jdbi.getConfig(JdbiImmutables.class);
        immutables.registerImmutable(Row.class);
        immutables.registerModifiable(MutableRow.class);

        jdbi.useHandle(handle -> {
            Row immutable = handle.createQuery("select 51 id, 'immutable' label").mapTo(Row.class).one();
            MutableRow modifiable = handle.createQuery("select 52 id, 'modifiable' label")
                    .mapTo(MutableRow.class)
                    .one();

            assertThat(immutable.id()).isEqualTo(51);
            assertThat(immutable.label()).isEqualTo("immutable");
            assertThat(modifiable.id()).isEqualTo(52);
            assertThat(modifiable.label()).isEqualTo("modifiable");
        });
    }

    public interface Row {
        int id();

        String label();
    }

    public interface MutableRow {
        int id();

        String label();
    }
}
