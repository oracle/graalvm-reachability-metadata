/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi.jdbi3_core;

import java.beans.ConstructorProperties;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbiConstructorsTest {
    @Test
    void mapsUsingImplicitSelectedAndFactoryConstructors() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:jdbi_constructors;DB_CLOSE_DELAY=-1");
        RowMapper<Person> implicit = ConstructorMapper.of(Person.class);
        RowMapper<SelectedPerson> selected =
                ConstructorMapper.of(JdbiConstructors.findConstructorFor(SelectedPerson.class));
        RowMapper<FactoryPerson> factory = ConstructorMapper.of(FactoryPerson.class, PersonFactories.class);

        jdbi.useHandle(handle -> {
            Person person = handle.createQuery("select 11 id, 'Ada' name").map(implicit).one();
            SelectedPerson selectedPerson = handle.createQuery("select 12 id, 'Lin' name").map(selected).one();
            FactoryPerson factoryPerson = handle.createQuery("select 13 id, 'Sam' name").map(factory).one();

            assertThat(person.description()).isEqualTo("11:Ada");
            assertThat(selectedPerson.description()).isEqualTo("12:Lin");
            assertThat(factoryPerson.description()).isEqualTo("13:Sam");
        });
    }

    public static class Person {
        private final int id;
        private final String name;

        @ConstructorProperties({"id", "name"})
        public Person(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public String description() {
            return id + ":" + name;
        }
    }

    public static class SelectedPerson {
        private final int id;
        private final String name;

        @JdbiConstructor
        public SelectedPerson(@ColumnName("id") int id, @ColumnName("name") String name) {
            this.id = id;
            this.name = name;
        }

        public SelectedPerson() {
            this(0, "");
        }

        public String description() {
            return id + ":" + name;
        }
    }

    public static class FactoryPerson {
        private final int id;
        private final String name;

        private FactoryPerson(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public String description() {
            return id + ":" + name;
        }
    }

    public static class PersonFactories {
        @JdbiConstructor
        public static FactoryPerson create(
                @ColumnName("id") int id, @ColumnName("name") String name) {
            return new FactoryPerson(id, name);
        }
    }
}
