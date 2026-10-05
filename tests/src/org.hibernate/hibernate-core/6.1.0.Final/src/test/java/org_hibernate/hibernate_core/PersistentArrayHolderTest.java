/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import static org.assertj.core.api.Assertions.assertThat;

public class PersistentArrayHolderTest {

    @Test
    public void persistsLoadsAndUpdatesAnEntityArray() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(ArrayOwner.class)
                .addAnnotatedClass(ArrayElement.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:persistent-array-holder")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            Long ownerId;
            try (Session session = factory.openSession()) {
                Transaction transaction = session.beginTransaction();
                ArrayOwner owner = new ArrayOwner();
                owner.setElements(new ArrayElement[]{new ArrayElement("first"), new ArrayElement("second")});
                session.persist(owner);
                transaction.commit();
                ownerId = owner.getId();
            }

            try (Session session = factory.openSession()) {
                Transaction transaction = session.beginTransaction();
                ArrayOwner loaded = session.find(ArrayOwner.class, ownerId);
                assertThat(loaded.getElements())
                        .extracting(ArrayElement::getValue)
                        .containsExactly("first", "second");

                loaded.setElements(new ArrayElement[]{loaded.getElements()[1]});
                transaction.commit();
            }

            try (Session session = factory.openSession()) {
                ArrayOwner loaded = session.find(ArrayOwner.class, ownerId);
                assertThat(loaded.getElements())
                        .extracting(ArrayElement::getValue)
                        .containsExactly("second");
            }
        }
    }

    @Entity(name = "ArrayOwner")
    @Table(name = "ARRAY_OWNER")
    public static class ArrayOwner {
        @Id
        @GeneratedValue
        private Long id;

        @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
        @JoinColumn(name = "owner_id")
        @OrderColumn(name = "element_index")
        private ArrayElement[] elements = new ArrayElement[0];

        public Long getId() {
            return id;
        }

        public ArrayElement[] getElements() {
            return elements;
        }

        public void setElements(ArrayElement[] elements) {
            this.elements = elements;
        }
    }

    @Entity(name = "ArrayElement")
    @Table(name = "ARRAY_ELEMENT")
    public static class ArrayElement {
        @Id
        @GeneratedValue
        private Long id;

        private String value;

        public ArrayElement() {
        }

        public ArrayElement(String value) {
            this.value = value;
        }

        public Long getId() {
            return id;
        }

        public String getValue() {
            return value;
        }
    }
}
