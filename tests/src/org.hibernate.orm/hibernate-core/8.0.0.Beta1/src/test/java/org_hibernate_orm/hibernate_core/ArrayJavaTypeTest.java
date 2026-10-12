/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayJavaTypeTest {

    @Test
    void objectArrayAttributeIsBoundQueriedAndDirtyChecked() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Post.class)
                .buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Post(1L, new String[] {"graal", "native"})));
            sessionFactory.inTransaction(session -> session.find(Post.class, 1L).getTags()[1] = "image");
            String[] tags = sessionFactory.fromTransaction(session -> session.find(Post.class, 1L).getTags());
            List<Long> matching = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select p.id from Post p where array_contains(p.tags, 'graal')", Long.class).getResultList());

            assertThat(tags).containsExactly("graal", "image");
            assertThat(matching).containsExactly(1L);
        }
    }

    @Entity(name = "Post")
    public static class Post {

        @Id
        private Long id;

        private String[] tags;

        public Post() {
        }

        public Post(Long id, String[] tags) {
            this.id = id;
            this.tags = tags;
        }

        public String[] getTags() {
            return tags;
        }
    }
}
