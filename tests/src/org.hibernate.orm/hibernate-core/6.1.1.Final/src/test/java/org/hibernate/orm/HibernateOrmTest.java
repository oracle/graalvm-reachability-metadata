/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.orm;

import java.util.Collections;
import java.util.Date;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

public class HibernateOrmTest {

    @Test
    void jpaBootstrap() {

        EntityManagerFactory emFactory = Persistence.createEntityManagerFactory("org.hibernate.orm");

        EntityManager entityManager = emFactory.createEntityManager();
        entityManager.getTransaction().begin();
        entityManager.persist(new Event("Our very first event!", new Date()));
        entityManager.persist(new Event("A follow up event", new Date()));
        entityManager.getTransaction().commit();
        entityManager.close();
    }

    @Test
    void hibernateBootstrap() {

        Configuration config = h2Config(Event.class);

        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        session.persist(new Event("Our very first event!", new Date()));
        session.persist(new Event("A follow up event", new Date()));

        session.close();
        sessionFactory.close();
    }

    @Test
    void relations() {

        Configuration config = h2Config(Cart.class, Item.class);

        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        Cart cart = new Cart();
        Item item = new Item();
        item.setCart(cart);
        cart.setItems(Collections.singleton(item));

        session.persist(cart);
        session.persist(item);

        Cart load = session.byId(Cart.class).load(cart.getId());

        session.close();
        sessionFactory.close();
    }

    @Test
    void idGenerator() {

        Configuration config = h2Config(User.class);

        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        User user = new User();
        user.setUsername("u1");

        session.persist(user);

        User load = session.byId(User.class).load(user.getId());

        session.close();
        sessionFactory.close();
    }

    private static Configuration h2Config(Class<?>... entities) {

        Configuration config = new Configuration();
        config.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        config.setProperty("hibernate.connection.url", "jdbc:h2:mem:test");
        config.setProperty("hibernate.connection.username", "");
        config.setProperty("hibernate.connection.password", "");
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        config.setProperty("hibernate.hbm2ddl.auto", "create-drop");

        for (Class<?> type : entities) {
            config.addAnnotatedClass(type);
        }

        return config;
    }
}
