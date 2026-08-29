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

public class ModifiablePojoPropertiesFactoryInnerModifiablePojoPropertiesTest {
    @Test
    void mapsRowsIntoAModifiableValue() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:modifiable_properties;DB_CLOSE_DELAY=-1");
        jdbi.getConfig(JdbiImmutables.class)
                .registerModifiable(Entry.class, MutableEntry.class, MutableEntry::new);

        Entry entry = jdbi.withHandle(handle -> handle.createQuery("select 81 id, 'mutable' label")
                .mapTo(Entry.class)
                .one());

        assertThat(entry.id()).isEqualTo(81);
        assertThat(entry.label()).isEqualTo("mutable");
    }

    public interface Entry {
        int id();

        String label();
    }

    public static final class MutableEntry implements Entry {
        private int id;
        private String label;

        public MutableEntry() {}

        @Override
        public int id() {
            return id;
        }

        public boolean idIsSet() {
            return true;
        }

        public MutableEntry setId(int newId) {
            id = newId;
            return this;
        }

        @Override
        public String label() {
            return label;
        }

        public MutableEntry setLabel(String newLabel) {
            label = newLabel;
            return this;
        }
    }
}
