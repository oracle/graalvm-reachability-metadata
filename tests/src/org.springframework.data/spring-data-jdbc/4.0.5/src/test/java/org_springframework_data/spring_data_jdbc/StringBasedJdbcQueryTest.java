/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.DataAccessStrategyFactory;
import org.springframework.data.jdbc.core.convert.DelegatingDataAccessStrategy;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.convert.QueryMappingConfiguration;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactory;
import org.springframework.data.relational.core.dialect.H2Dialect;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class StringBasedJdbcQueryTest {
    private EmbeddedDatabase database;
    private BookRepository repository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE inventory (id BIGINT PRIMARY KEY, title VARCHAR(100))");
        jdbc.update("INSERT INTO inventory (id, title) VALUES (?, ?)", 1L, "Orchard");
        jdbc.update("INSERT INTO inventory (id, title) VALUES (?, ?)", 2L, "Harbor");
        jdbc.update("INSERT INTO inventory (id, title) VALUES (?, ?)", 3L, "Summit");

        RelationalMappingContext context = new RelationalMappingContext();
        context.setSimpleTypeHolder(new JdbcCustomConversions().getSimpleTypeHolder());
        DelegatingDataAccessStrategy resolver = new DelegatingDataAccessStrategy();
        MappingJdbcConverter converter = new MappingJdbcConverter(context, resolver);
        DataAccessStrategy strategy = new DataAccessStrategyFactory(converter,
                new NamedParameterJdbcTemplate(jdbc), H2Dialect.INSTANCE, QueryMappingConfiguration.EMPTY).create();
        resolver.setDelegate(strategy);
        JdbcRepositoryFactory factory = new JdbcRepositoryFactory(new JdbcAggregateTemplate(converter, strategy));
        repository = factory.getRepository(BookRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void instantiatesAnnotationConfiguredRowMapperAndBindsNamedParameter() {
        assertEquals(List.of(new Book(2L, "HARBOR"), new Book(3L, "SUMMIT")), repository.findMappedBooks(2L));
        assertEquals(List.of(new Book(3L, "SUMMIT")), repository.findMappedBooks(3L));
    }

    @Test
    void instantiatesAnnotationConfiguredResultSetExtractor() {
        assertEquals(List.of(new Book(2L, "Harbor:catalog"), new Book(3L, "Summit:catalog")),
                repository.findExtractedBooks(2L));
        assertEquals(List.of(), repository.findExtractedBooks(4L));
    }

    public interface BookRepository extends Repository<Book, Long> {
        @Query(value = """
                SELECT id, title FROM inventory WHERE id >= :minimum ORDER BY id
                """, rowMapperClass = UppercaseBookMapper.class)
        List<Book> findMappedBooks(@Param("minimum") long minimum);

        @Query(value = """
                SELECT id, title FROM inventory WHERE id >= :minimum ORDER BY id
                """, resultSetExtractorClass = CatalogBookExtractor.class)
        List<Book> findExtractedBooks(@Param("minimum") long minimum);
    }

    public record Book(@Id Long id, String title) {
    }

    public static class UppercaseBookMapper implements RowMapper<Book> {
        public UppercaseBookMapper() {
        }

        @Override
        public Book mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new Book(resultSet.getLong("id"), resultSet.getString("title").toUpperCase(Locale.ROOT));
        }
    }

    public static class CatalogBookExtractor implements ResultSetExtractor<List<Book>> {
        public CatalogBookExtractor() {
        }

        @Override
        public List<Book> extractData(ResultSet resultSet) throws SQLException {
            List<Book> books = new ArrayList<>();
            while (resultSet.next()) {
                books.add(new Book(resultSet.getLong("id"), resultSet.getString("title") + ":catalog"));
            }
            return books;
        }
    }
}
