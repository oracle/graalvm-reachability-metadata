/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@MicronautTest(startApplication = false, transactional = false)
@Property(
        name = "datasources.default.url",
        value = "jdbc:h2:mem:catalog;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
@Timeout(45)
public class MicronautDataJdbcTest {

    @Inject
    CatalogBookRepository repository;

    @Inject
    VersionedBookRepository versionedBookRepository;

    @BeforeEach
    void clearCatalog() {
        repository.deleteAll();
    }

    @Test
    void performsGeneratedCrudOperations() {
        CatalogBook saved = repository.save(
                new CatalogBook("978-0-00-000001-1", "Compiler Journeys", "A. Rivera", 280));

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.existsById(saved.getId())).isTrue();

        CatalogBook found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Compiler Journeys");
        assertThat(found.getAuthor()).isEqualTo("A. Rivera");
        assertThat(found.getPages()).isEqualTo(280);

        found.setTitle("Compiler Journeys, Revised");
        found.setPages(312);
        repository.update(found);

        CatalogBook updated = repository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Compiler Journeys, Revised");
        assertThat(updated.getPages()).isEqualTo(312);
        assertThat(repository.count()).isEqualTo(1);

        repository.deleteById(saved.getId());
        assertThat(repository.findById(saved.getId())).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void executesGeneratedFinderQueriesForPersistedEntities() {
        List<CatalogBook> saved = repository.saveAll(List.of(
                new CatalogBook("978-0-00-000010-3", "Alpine Systems", "Dana Chen", 240),
                new CatalogBook("978-0-00-000011-0", "Zenith Systems", "Dana Chen", 480),
                new CatalogBook("978-0-00-000012-7", "Practical Storage", "Morgan Lee", 360)));

        assertThat(saved).hasSize(3).extracting(CatalogBook::getId).doesNotContainNull();
        Optional<CatalogBook> foundByIsbn = repository.findByIsbn("978-0-00-000012-7");
        assertThat(foundByIsbn).isPresent();
        assertThat(foundByIsbn.orElseThrow().getTitle()).isEqualTo("Practical Storage");
        assertThat(repository.findByAuthorOrderByTitle("Dana Chen"))
                .extracting(CatalogBook::getTitle)
                .containsExactly("Alpine Systems", "Zenith Systems");
        assertThat(repository.findByPagesGreaterThanOrderByPagesDesc(300))
                .extracting(CatalogBook::getPages)
                .containsExactly(480, 360);
        assertThat(repository.countByAuthor("Dana Chen")).isEqualTo(2);
        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    void returnsSortedPagesWithTotalCounts() {
        repository.saveAll(List.of(
                new CatalogBook("978-0-00-000020-2", "Ember", "Riley Shah", 180),
                new CatalogBook("978-0-00-000021-9", "Atlas", "Riley Shah", 220),
                new CatalogBook("978-0-00-000022-6", "Delta", "Riley Shah", 260),
                new CatalogBook("978-0-00-000023-3", "Beacon", "Riley Shah", 300),
                new CatalogBook("978-0-00-000024-0", "Cinder", "Riley Shah", 340)));

        Pageable request = Pageable.from(1, 2, Sort.of(Sort.Order.asc("title")));
        Page<CatalogBook> page = repository.findAll(request);

        assertThat(page.getContent())
                .extracting(CatalogBook::getTitle)
                .containsExactly("Cinder", "Delta");
        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getNumberOfElements()).isEqualTo(2);
        assertThat(page.getTotalSize()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void usesEntityVersionsToRejectStaleUpdates() {
        VersionedBook saved = versionedBookRepository.save(new VersionedBook("Distributed Catalogs"));
        VersionedBook staleCopy = versionedBookRepository.findById(saved.getId()).orElseThrow();
        VersionedBook currentCopy = versionedBookRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved.getVersion()).isZero();

        currentCopy.setTitle("Distributed Catalogs, Second Edition");
        versionedBookRepository.update(currentCopy);

        VersionedBook updated = versionedBookRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Distributed Catalogs, Second Edition");
        assertThat(updated.getVersion()).isEqualTo(1L);

        staleCopy.setTitle("Outdated Catalogs");
        assertThatThrownBy(() -> versionedBookRepository.update(staleCopy))
                .isInstanceOf(OptimisticLockException.class);

        VersionedBook retained = versionedBookRepository.findById(saved.getId()).orElseThrow();
        assertThat(retained.getTitle()).isEqualTo("Distributed Catalogs, Second Edition");
        assertThat(retained.getVersion()).isEqualTo(1L);
    }

    @JdbcRepository(dialect = Dialect.H2)
    public interface CatalogBookRepository extends CrudRepository<CatalogBook, Long> {

        Optional<CatalogBook> findByIsbn(String isbn);

        Page<CatalogBook> findAll(Pageable pageable);

        List<CatalogBook> findByAuthorOrderByTitle(String author);

        List<CatalogBook> findByPagesGreaterThanOrderByPagesDesc(int pages);

        long countByAuthor(String author);
    }

    @JdbcRepository(dialect = Dialect.H2)
    public interface VersionedBookRepository extends CrudRepository<VersionedBook, Long> {
    }
}

@MappedEntity("versioned_book")
class VersionedBook {

    @Id
    @GeneratedValue
    private Long id;

    @Version
    private Long version;

    private String title;

    VersionedBook() {
    }

    VersionedBook(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}

@MappedEntity("catalog_book")
class CatalogBook {

    @Id
    @GeneratedValue
    private Long id;

    private String isbn;
    private String title;
    private String author;
    private int pages;

    CatalogBook() {
    }

    CatalogBook(String isbn, String title, String author, int pages) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.pages = pages;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
