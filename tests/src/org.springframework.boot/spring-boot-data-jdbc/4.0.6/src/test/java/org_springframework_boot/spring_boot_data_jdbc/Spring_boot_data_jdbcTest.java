/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcDatabaseDialect;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcProperties;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_data_jdbcTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class, JdbcClientAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, DataJdbcRepositoriesAutoConfiguration.class))
            .withUserConfiguration(TestApplication.class);

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(DataJdbcRepositoriesAutoConfiguration.class.getName());
    }

    @Test
    void dataJdbcPropertiesExposeConfiguredDatabaseDialect() {
        DataJdbcProperties properties = new DataJdbcProperties();

        assertThat(properties.getDialect()).isNull();
        for (DataJdbcDatabaseDialect dialect : DataJdbcDatabaseDialect.values()) {
            properties.setDialect(dialect);

            assertThat(properties.getDialect()).isSameAs(dialect);
            assertThat(DataJdbcDatabaseDialect.valueOf(dialect.name())).isSameAs(dialect);
        }

        properties.setDialect(null);
        assertThat(properties.getDialect()).isNull();
    }

    @Test
    void autoConfigurationBindsDialectAndCreatesJdbcRepositoryInfrastructure() {
        this.contextRunner.withPropertyValues(dataSourceProperties("spring.data.jdbc.dialect=h2"))
                .run((context) -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).hasSingleBean(NamedParameterJdbcOperations.class);
                    assertThat(context).hasSingleBean(PlatformTransactionManager.class);
                    assertThat(context).hasSingleBean(DataJdbcProperties.class);
                    assertThat(context).hasSingleBean(JdbcMappingContext.class);
                    assertThat(context).hasSingleBean(JdbcAggregateTemplate.class);
                    assertThat(context).hasSingleBean(PersonRepository.class);

                    DataJdbcProperties properties = context.getBean(DataJdbcProperties.class);
                    assertThat(properties.getDialect()).isSameAs(DataJdbcDatabaseDialect.H2);

                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    createPeopleTable(jdbcTemplate);

                    PersonRepository repository = context.getBean(PersonRepository.class);
                    Person ada = repository.save(new Person("Ada", 42));
                    Person grace = repository.save(new Person("Grace", 37));

                    assertThat(ada.getId()).isNotNull();
                    assertThat(grace.getId()).isNotNull();
                    assertThat(repository.count()).isEqualTo(2);
                    assertThat(repository.findById(ada.getId())).hasValueSatisfying((person) -> {
                        assertThat(person.getName()).isEqualTo("Ada");
                        assertThat(person.getAge()).isEqualTo(42);
                    });
                    assertThat(repository.findAll()).extracting(Person::getName).containsExactlyInAnyOrder("Ada",
                            "Grace");

                    ada.setAge(43);
                    repository.save(ada);
                    assertThat(repository.findById(ada.getId())).hasValueSatisfying(
                            (person) -> assertThat(person.getAge()).isEqualTo(43));

                    repository.delete(grace);
                    assertThat(repository.findAll()).extracting(Person::getName).containsExactly("Ada");
                    jdbcTemplate.execute("SHUTDOWN");
                });
    }

    @Test
    void repositoryQueriesUseSpringDataJdbcMappingAndDerivedQueryMethods() {
        this.contextRunner.withPropertyValues(dataSourceProperties("spring.data.jdbc.dialect=h2"))
                .run((context) -> {
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    createPeopleTable(jdbcTemplate);

                    PersonRepository repository = context.getBean(PersonRepository.class);
                    repository.save(new Person("Ada", 42));
                    repository.save(new Person("Ada", 29));
                    repository.save(new Person("Grace", 37));

                    assertThat(repository.findByName("Ada")).extracting(Person::getAge).containsExactlyInAnyOrder(42,
                            29);
                    assertThat(repository.findByAgeGreaterThan(35)).extracting(Person::getName)
                            .containsExactlyInAnyOrder("Ada", "Grace");
                    assertThat(repository.existsById(repository.findByName("Grace").get(0).getId())).isTrue();

                    jdbcTemplate.execute("SHUTDOWN");
                });
    }

    @Test
    void repositoryAutoConfigurationCanBeDisabled() {
        this.contextRunner
                .withPropertyValues(dataSourceProperties("spring.data.jdbc.dialect=h2",
                        "spring.data.jdbc.repositories.enabled=false"))
                .run((context) -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(JdbcTemplate.class);
                    assertThat(context).doesNotHaveBean(DataJdbcProperties.class);
                    assertThat(context).doesNotHaveBean(JdbcAggregateTemplate.class);
                    assertThat(context).doesNotHaveBean(PersonRepository.class);
                });
    }

    private static String[] dataSourceProperties(String... additionalProperties) {
        String databaseName = "datajdbc_" + UUID.randomUUID().toString().replace('-', '_');
        List<String> properties = new ArrayList<>(List.of(
                "spring.datasource.type=org.springframework.jdbc.datasource.SimpleDriverDataSource",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=never"));
        properties.addAll(List.of(additionalProperties));
        return properties.toArray(String[]::new);
    }

    private static void createPeopleTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE "people" (
                    "ID" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    "name" VARCHAR(255) NOT NULL,
                    "age" INTEGER NOT NULL
                )
                """);
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    static class TestApplication {
    }

}

@Table("people")
class Person {

    @Id
    private Long id;

    @Column("name")
    private String name;

    @Column("age")
    private int age;

    Person() {
    }

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    Long getId() {
        return this.id;
    }

    String getName() {
        return this.name;
    }

    int getAge() {
        return this.age;
    }

    void setAge(int age) {
        this.age = age;
    }

}

interface PersonRepository extends CrudRepository<Person, Long> {

    List<Person> findByName(String name);

    List<Person> findByAgeGreaterThan(int age);

}
