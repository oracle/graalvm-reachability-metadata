/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
public class Book {
    @Id
    @GeneratedValue
    private Integer id;

    @Size(min = 13, max = 13)
    private String isbn;

    @NotNull
    @Size(max = 100)
    private String title;

    @NotNull
    @Past
    private LocalDate published;

    public byte[] coverImage;

    @NotNull
    @ManyToOne
    private Author author;

    public Book() {
    }

    public Book(String isbn, String title, Author author, LocalDate published) {
        this.title = title;
        this.isbn = isbn;
        this.author = author;
        this.published = published;
        this.coverImage = ("Cover image for '" + title + "'").getBytes();
    }

    public Integer getId() {
        return id;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public Author getAuthor() {
        return author;
    }

    public LocalDate getPublished() {
        return published;
    }

    public byte[] getCoverImage() {
        return coverImage;
    }
}
