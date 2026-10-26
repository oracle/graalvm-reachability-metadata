/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.NaturalId;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises Hibernate mutable natural IDs to look up loyalty members by their current email address.
@Timeout(60)
public class LoyaltyMemberNaturalIdTest {

    @Test
    public void changesTheLoginEmailWithoutChangingTheMemberOrLosingRewardPoints() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Member.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:loyalty_natural_ids;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                session.persist(new Member(1L, "ada@old.example", "Ada", 120));
                session.persist(new Member(2L, "ben@example.test", "Ben", 80));
            });

            factory.inTransaction(session -> {
                Member member = session.bySimpleNaturalId(Member.class).load("ada@old.example");
                assertNotNull(member);
                assertEquals(1L, member.id);
                assertEquals("Ada", member.name);
                assertEquals(120, member.rewardPoints);

                member.email = "ada@new.example";
                member.rewardPoints += 30;
                session.flush();

                assertNull(session.bySimpleNaturalId(Member.class).load("ada@old.example"));
                assertSame(member, session.bySimpleNaturalId(Member.class).load("ada@new.example"));
            });

            factory.inTransaction(session -> {
                assertNull(session.bySimpleNaturalId(Member.class).load("ada@old.example"));
                Member member = session.bySimpleNaturalId(Member.class).load("ada@new.example");
                assertNotNull(member);
                assertEquals(1L, member.id);
                assertEquals("Ada", member.name);
                assertEquals("ada@new.example", member.email);
                assertEquals(150, member.rewardPoints);
                assertSame(member, session.find(Member.class, 1L));

                Member other = session.bySimpleNaturalId(Member.class).load("ben@example.test");
                assertNotNull(other);
                assertEquals(2L, other.id);
                assertEquals("Ben", other.name);
                assertEquals(80, other.rewardPoints);
                assertNull(session.bySimpleNaturalId(Member.class).load("unknown@example.test"));
                assertEquals(2L, session.createQuery(
                        "select count(m) from LoyaltyMember m", Long.class).getSingleResult());
            });
        }
    }

    @Entity(name = "LoyaltyMember")
    @Table(name = "loyalty_member")
    public static final class Member {
        @Id
        private Long id;

        @NaturalId(mutable = true)
        @Column(nullable = false)
        private String email;

        private String name;

        private int rewardPoints;

        public Member() {
        }

        public Member(Long id, String email, String name, int rewardPoints) {
            this.id = id;
            this.email = email;
            this.name = name;
            this.rewardPoints = rewardPoints;
        }
    }
}
