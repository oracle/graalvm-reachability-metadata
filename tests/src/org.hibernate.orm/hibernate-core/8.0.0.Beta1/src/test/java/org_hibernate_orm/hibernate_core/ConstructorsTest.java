/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorsTest {

    @Test
    void annotationDrivenValueGeneratorsPopulateTimestamps() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Document.class)
                .buildSessionFactory()) {
            Instant before = Instant.now().minusSeconds(60);
            sessionFactory.inTransaction(session -> session.persist(new Document(1L, "draft")));
            Document document = sessionFactory.fromTransaction(session -> session.find(Document.class, 1L));

            assertThat(document.getCreatedAt()).isAfter(before);
            assertThat(document.getUpdatedAt()).isAfter(before);
        }
    }

    @Entity(name = "Document")
    public static class Document {

        @Id
        private Long id;

        private String content;

        @CreationTimestamp
        private Instant createdAt;

        @UpdateTimestamp
        private Instant updatedAt;

        public Document() {
        }

        public Document(Long id, String content) {
            this.id = id;
            this.content = content;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }
}
