/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.envers;

import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class HibernateOrmTest {

    @Test
    void auditingTest() {

        Event event = new Event("Our very first event!", new Date());

        Configuration config = h2Config(Event.class);
        SessionFactory sessionFactory = config.buildSessionFactory();
        Session session = sessionFactory.openSession();

        session.getTransaction().begin();
        session.persist(event);
        session.getTransaction().commit();

        AuditReader auditReader = AuditReaderFactory.get(session);
        List<Number> revisionNumbers = auditReader.getRevisions(Event.class, event.getId());
        Assert.assertTrue(revisionNumbers.size() == 1);

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
