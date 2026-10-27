/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Hibernate association cascades and orphan removal while managing support-ticket comments.
@Timeout(60)
public class SupportTicketCascadeTest {

    @Test
    public void persistsCommentsWithTheirTicketAndRemovesOnlyTheOwnedComments() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(Ticket.class)
                .addAnnotatedClass(Comment.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:support_ticket_cascades;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.cache.use_second_level_cache", "false")
                .setProperty("jakarta.persistence.validation.mode", "none");

        try (SessionFactory factory = configuration.buildSessionFactory()) {
            factory.inTransaction(session -> {
                Ticket ticket = new Ticket(10L, "Missing parcel");
                ticket.addComment(new Comment(101L, "Checking with the carrier"));
                ticket.addComment(new Comment(102L, "Duplicate reply"));
                session.persist(ticket);

                Ticket other = new Ticket(20L, "Change delivery address");
                other.addComment(new Comment(201L, "Address updated"));
                session.persist(other);
            });

            factory.inTransaction(session -> {
                Ticket ticket = session.find(Ticket.class, 10L);
                assertNotNull(ticket);
                assertEquals("Missing parcel", ticket.subject);
                assertEquals(List.of("Checking with the carrier", "Duplicate reply"),
                        ticket.comments.stream().map(comment -> comment.message).toList());
                assertEquals(List.of(10L, 10L),
                        ticket.comments.stream().map(comment -> comment.ticket.id).toList());

                Comment duplicate = ticket.comments.get(1);
                ticket.removeComment(duplicate);
                ticket.addComment(new Comment(103L, "Replacement dispatched"));
            });

            factory.inTransaction(session -> {
                assertNull(session.find(Comment.class, 102L));
                Ticket ticket = session.find(Ticket.class, 10L);
                assertNotNull(ticket);
                assertEquals(List.of(101L, 103L), ticket.comments.stream().map(comment -> comment.id).toList());
                assertEquals(List.of("Checking with the carrier", "Replacement dispatched"),
                        ticket.comments.stream().map(comment -> comment.message).toList());

                Comment replacement = session.find(Comment.class, 103L);
                assertNotNull(replacement);
                assertEquals("Missing parcel", replacement.ticket.subject);
                session.remove(ticket);
            });

            factory.inTransaction(session -> {
                assertNull(session.find(Ticket.class, 10L));
                assertNull(session.find(Comment.class, 101L));
                assertNull(session.find(Comment.class, 103L));
                Ticket other = session.find(Ticket.class, 20L);
                assertNotNull(other);
                assertEquals("Change delivery address", other.subject);
                assertEquals(List.of("Address updated"),
                        other.comments.stream().map(comment -> comment.message).toList());
                assertEquals(List.of(201L), session.createQuery(
                        "select c.id from SupportTicketComment c order by c.id", Long.class).getResultList());
                assertEquals(1L, session.createQuery(
                        "select count(t) from SupportTicket t", Long.class).getSingleResult());
            });
        }
    }

    @Entity(name = "SupportTicket")
    @Table(name = "support_ticket")
    public static final class Ticket {
        @Id
        private Long id;

        @Column(nullable = false)
        private String subject;

        @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("id ASC")
        private List<Comment> comments = new ArrayList<>();

        public Ticket() {
        }

        public Ticket(Long id, String subject) {
            this.id = id;
            this.subject = subject;
        }

        public void addComment(Comment comment) {
            comments.add(comment);
            comment.ticket = this;
        }

        public void removeComment(Comment comment) {
            comments.remove(comment);
            comment.ticket = null;
        }
    }

    @Entity(name = "SupportTicketComment")
    @Table(name = "support_ticket_comment")
    public static final class Comment {
        @Id
        private Long id;

        @Column(nullable = false)
        private String message;

        @ManyToOne(fetch = FetchType.EAGER, optional = false)
        @JoinColumn(name = "ticket_id", nullable = false)
        private Ticket ticket;

        public Comment() {
        }

        public Comment(Long id, String message) {
            this.id = id;
            this.message = message;
        }
    }
}
