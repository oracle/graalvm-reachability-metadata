/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org_hibernate_orm.hibernate_core.entity.CardPayment;
import org_hibernate_orm.hibernate_core.entity.Payment;
import org_hibernate_orm.hibernate_core.entity.Truck;
import org_hibernate_orm.hibernate_core.entity.Vehicle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entities mapped with the JOINED and TABLE_PER_CLASS inheritance strategies.
 * Hibernate creates a JoinedSubclassEntityPersister or a UnionSubclassEntityPersister
 * for them by reflection.
 */
public class InheritanceStrategyTest {

    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    public void close() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
            entityManagerFactory = null;
        }
    }

    @Test
    public void testJoinedInheritance() {
        EntityManager entityManager = createEntityManager("JoinedPU");
        CardPayment payment = new CardPayment();
        payment.setId(1L);
        payment.setAmount(250L);
        payment.setCardNumber("1234");
        entityManager.getTransaction().begin();
        entityManager.persist(payment);
        entityManager.getTransaction().commit();
        entityManager.clear();

        List<Payment> payments = entityManager.createQuery("from Payment", Payment.class).getResultList();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0)).isInstanceOf(CardPayment.class);
        assertThat(payments.get(0).getAmount()).isEqualTo(250L);
        assertThat(((CardPayment) payments.get(0)).getCardNumber()).isEqualTo("1234");
        entityManager.close();
    }

    @Test
    public void testTablePerClassInheritance() {
        EntityManager entityManager = createEntityManager("TablePerClassPU");
        Truck truck = new Truck();
        truck.setId(1L);
        truck.setManufacturer("ACME");
        truck.setAxles(3);
        entityManager.getTransaction().begin();
        entityManager.persist(truck);
        entityManager.getTransaction().commit();
        entityManager.clear();

        List<Vehicle> vehicles = entityManager.createQuery("from Vehicle", Vehicle.class).getResultList();
        assertThat(vehicles).hasSize(1);
        assertThat(vehicles.get(0)).isInstanceOf(Truck.class);
        assertThat(vehicles.get(0).getManufacturer()).isEqualTo("ACME");
        assertThat(((Truck) vehicles.get(0)).getAxles()).isEqualTo(3);
        entityManager.close();
    }

    private EntityManager createEntityManager(String persistenceUnit) {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:" + persistenceUnit);
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit, properties);
        return entityManagerFactory.createEntityManager();
    }
}
