/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorInstanceFactoryTest {
    @Test
    void mapsARecordThroughItsCanonicalConstructor() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:constructor_factory;DB_CLOSE_DELAY=-1");

        Book book = jdbi.withHandle(handle -> handle.createQuery("select 21 book_id, 'Native' title")
                .map(ConstructorMapper.of(Book.class))
                .one());

        assertThat(book.id()).isEqualTo(21);
        assertThat(book.title()).isEqualTo("Native");
    }

    public record Book(@ColumnName("book_id") int id, String title) {}
}
