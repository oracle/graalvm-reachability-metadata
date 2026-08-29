/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldMapperTest {
    @Test
    void discoversAndMapsDeclaredFields() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:field_mapper;DB_CLOSE_DELAY=-1");

        Account account = jdbi.withHandle(handle -> handle.createQuery("select 31 id, 'checking' label")
                .map(FieldMapper.of(Account.class))
                .one());

        assertThat(account.id()).isEqualTo(31);
        assertThat(account.label()).isEqualTo("checking");
    }

    public static class Account {
        private int id;
        private String label;

        public Account() {}

        public int id() {
            return id;
        }

        public String label() {
            return label;
        }
    }
}
