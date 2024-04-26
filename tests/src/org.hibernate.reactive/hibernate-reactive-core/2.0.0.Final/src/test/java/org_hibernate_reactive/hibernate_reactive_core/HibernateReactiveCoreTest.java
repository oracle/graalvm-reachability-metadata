/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import jakarta.persistence.Persistence;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org_hibernate_reactive.hibernate_reactive_core.entity.Author;
import org_hibernate_reactive.hibernate_reactive_core.entity.Book;
import org_hibernate_reactive.hibernate_reactive_core.entity.Course;
import org_hibernate_reactive.hibernate_reactive_core.entity.Student;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

import static java.time.Month.JANUARY;
import static java.time.Month.JUNE;
import static java.time.Month.MAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.reactive.stage.Stage.SessionFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HibernateReactiveCoreTest {

    private static final Logger logger = LoggerFactory.getLogger("HibernateReactiveCoreTest");

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/" + DATABASE;

    private static Process process;

    private SessionFactory factory;

    @BeforeAll
    public void init() throws IOException {
        logger.info("Starting MySQL ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", "3306:3306", "-e", "MYSQL_DATABASE=" + DATABASE, "-e", "MYSQL_USER=" + USERNAME,
                "-e", "MYSQL_PASSWORD=" + PASSWORD, "container-registry.oracle.com/mysql/community-server:8.2").inheritIO().start();

        waitUntil(() -> {
            openConnection().close();
            return true;
        }, 300, 1);

        logger.info("MySQL started");

        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", JDBC_URL);
        properties.put("jakarta.persistence.jdbc.user", USERNAME);
        properties.put("jakarta.persistence.jdbc.password", PASSWORD);
        factory = Persistence.createEntityManagerFactory("mysql-example", properties).unwrap(SessionFactory.class);
    }

    @AfterAll
    public void close() {
        if (factory != null) {
            factory.close();
        }
        if (process != null && process.isAlive()) {
            logger.info("Shutting down MySQL");
            process.destroy();
        }
    }

    // not using Awaitility library because of `com.oracle.svm.core.jdk.UnsupportedFeatureError: ThreadMXBean methods` issue
    // which happens if a condition is not fulfilled when a test is running in a native image
    private void waitUntil(Callable<Boolean> conditionEvaluator, int timeoutSeconds, int sleepTimeSeconds) {
        Exception lastException = null;

        long end  = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(sleepTimeSeconds * 1000L);
            } catch (InterruptedException e) {
                // continue
            }
            try {
                if (conditionEvaluator.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        String errorMessage = "Condition was not fulfilled within " + timeoutSeconds + " seconds";
        throw lastException == null ? new IllegalStateException(errorMessage) : new IllegalStateException(errorMessage, lastException);
    }

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

    @Test
    void testSaveAndLoad() {
        Author author1 = new Author("Iain M. Banks");
        Author author2 = new Author("Neal Stephenson");
        Book book1 = new Book("1-85723-235-6", "Feersum Endjinn", author1, LocalDate.of(1994, JANUARY, 1));
        Book book2 = new Book("0-380-97346-4", "Cryptonomicon", author2, LocalDate.of(1999, MAY, 1));
        Book book3 = new Book("0-553-08853-X", "Snow Crash", author2, LocalDate.of(1992, JUNE, 1));
        author1.getBooks().add(book1);
        author2.getBooks().add(book2);
        author2.getBooks().add(book3);

        // persist the Authors with their Books in a transaction
        factory.withTransaction((session, tx) -> session.persist(author1, author2))
                .toCompletableFuture()
                .join();

        // retrieve a Book
        Book book = factory.withSession(session -> session.find(Book.class, book1.getId()))
                .toCompletableFuture()
                .join();
        assertThat(book.getTitle()).isEqualTo(book1.getTitle());

        // retrieve both Authors at once
        List<Author> authors = factory.withSession(session -> session.find(Author.class, author1.getId(), author2.getId()))
                .toCompletableFuture()
                .join();
        assertThat(authors)
                .hasSize(2)
                .extracting(Author::getName)
                .containsExactly(author1.getName(), author2.getName());

        // query the Book titles
        List<Object[]> titleRows = factory.withSession(session -> session.createQuery("select title, author.name from Book order by title desc", Object[].class)
                                .getResultList())
                .toCompletableFuture()
                .join();
        assertThat(titleRows)
                .hasSize(3)
                .extracting(it -> it[0], it -> it[1])
                .containsExactly(
                        Tuple.tuple(book3.getTitle(), author2.getName()),
                        Tuple.tuple(book1.getTitle(), author1.getName()),
                        Tuple.tuple(book2.getTitle(), author2.getName()));
    }

    /**
     * Tests @{@link jakarta.persistence.ManyToMany} and @{@link jakarta.persistence.ElementCollection} relations with Hibernate Reactive.
     */
    @Test
    void testCollections() {
        final Course languageCourse = new Course("English");
        languageCourse.setNotes(List.of("Starting in December"));
        final Course mathCourse = new Course("Mathematics");

        // Save courses
        factory.withTransaction((session, tx) -> session.persist(languageCourse, mathCourse))
                .toCompletableFuture()
                .join();

        // retrieve a Course
        Course loadedLanguageCourse = factory.withSession(session -> session.find(Course.class, languageCourse.getId()))
                .toCompletableFuture()
                .join();
        assertThat(loadedLanguageCourse).isNotNull();
        assertThat(loadedLanguageCourse.getStudents().size()).isEqualTo(0);
        assertThat(loadedLanguageCourse.getNotes().size()).isEqualTo(1);

        Course loadedMathCourse = factory.withSession(session -> session.find(Course.class, mathCourse.getId()))
                .toCompletableFuture()
                .join();
        assertThat(loadedMathCourse).isNotNull();
        assertThat(loadedMathCourse.getStudents().size()).isEqualTo(0);
        assertThat(loadedMathCourse.getNotes().size()).isEqualTo(0);

        final Student student = new Student("Peter", Set.of(mathCourse));
        // Save Student
        factory.withTransaction((session, tx) -> session.persist(student))
                .toCompletableFuture()
                .join();
        Long id = student.getId();
        assertThat(id).isNotNull();

        // retrieve a Student and verify that student has course assigned
        Student loadedStudent = factory.withSession(session -> session.find(Student.class, student.getId()))
                .toCompletableFuture()
                .join();
        assertThat(loadedStudent).isNotNull();
        assertThat(loadedStudent.getName()).isEqualTo("Peter");
        assertThat(loadedStudent.getCourses().size()).isEqualTo(1);

        loadedLanguageCourse = factory.withSession(session -> session.find(Course.class, languageCourse.getId()))
                .toCompletableFuture()
                .join();
        assertThat(loadedLanguageCourse).isNotNull();
        assertThat(loadedLanguageCourse.getStudents().size()).isEqualTo(0);

        loadedMathCourse = factory.withSession(session -> session.find(Course.class, mathCourse.getId()))
                .toCompletableFuture()
                .join();
        assertThat(loadedMathCourse).isNotNull();
        assertThat(loadedMathCourse.getStudents().size()).isEqualTo(1);
    }
}
