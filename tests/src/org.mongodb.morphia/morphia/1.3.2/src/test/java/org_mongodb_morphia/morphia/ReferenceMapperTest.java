/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import com.mongodb.DBObject;
import com.mongodb.DBRef;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Reference;

import static org.assertj.core.api.Assertions.assertThat;

public class ReferenceMapperTest {
    @Test
    public void serializesSingleReferenceUsingReferencedEntityId() {
        final Morphia morphia = new Morphia();
        morphia.map(Book.class, Author.class);
        final Book book = new Book("book-1", new Author("author-1"));

        final DBObject document = morphia.toDBObject(book);

        assertThat(document.get("author")).isInstanceOf(DBRef.class);
        final DBRef authorReference = (DBRef) document.get("author");
        assertThat(authorReference.getCollectionName()).isEqualTo("reference_mapper_authors");
        assertThat(authorReference.getId()).isEqualTo("author-1");
    }

    @Entity("reference_mapper_books")
    public static final class Book {
        @Id
        private String id;
        @Reference
        private Author author;

        private Book() {
        }

        private Book(final String id, final Author author) {
            this.id = id;
            this.author = author;
        }
    }

    @Entity("reference_mapper_authors")
    public static final class Author {
        @Id
        private String id;

        private Author() {
        }

        private Author(final String id) {
            this.id = id;
        }
    }
}
