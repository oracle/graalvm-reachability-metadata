/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityCallbackTest {

    @Test
    void lifecycleCallbackMethodsOnTheEntityAreInvoked() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Article.class)
                .buildSessionFactory()) {
            Article article = new Article(1L, "native images");
            sessionFactory.inTransaction(session -> session.persist(article));
            Article loaded = sessionFactory.fromTransaction(session -> session.find(Article.class, 1L));

            assertThat(article.getSlug()).isEqualTo("native-images");
            assertThat(loaded.getSlug()).isEqualTo("native-images");
            assertThat(loaded.isLoaded()).isTrue();
        }
    }

    @Entity(name = "Article")
    public static class Article {

        @Id
        private Long id;

        private String title;

        private String slug;

        @Transient
        private boolean loaded;

        public Article() {
        }

        public Article(Long id, String title) {
            this.id = id;
            this.title = title;
        }

        @PrePersist
        void computeSlug() {
            slug = title.replace(' ', '-');
        }

        @PostLoad
        void markLoaded() {
            loaded = true;
        }

        public String getSlug() {
            return slug;
        }

        public boolean isLoaded() {
            return loaded;
        }
    }
}
