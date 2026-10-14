/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;

/// A minimal entity shared by the query result instantiation scenarios.
@Entity(name = "Person")
public class Person {

    @Id
    private Long id;

    private String name;

    private Integer age;

    public Person() {
    }

    public Person(Long id, String name, Integer age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getAge() {
        return age;
    }

    /// Persists the two people every query scenario reads back.
    public static void persistAliceAndBob(SessionFactory sessionFactory) {
        sessionFactory.inTransaction(session -> {
            session.persist(new Person(1L, "Alice", 31));
            session.persist(new Person(2L, "Bob", 27));
        });
    }
}
