/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.query.QueryScanConsistency;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.Query;
import org.springframework.data.couchbase.core.query.QueryCriteria;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.data.mapping.model.BeanWrapperPropertyAccessorFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Timeout(60)
public class Spring_data_couchbaseTest {
    private static final String IMAGE = "couchbase/server:community-7.6.2";
    private static final String USERNAME = "Administrator";
    private static final String PASSWORD = "password";
    private static final String BUCKET = "spring-data";

    @Container
    private static final CouchbaseContainer COUCHBASE = new CouchbaseContainer(IMAGE)
            .withCredentials(USERNAME, PASSWORD)
            .withBucket(new BucketDefinition(BUCKET).withPrimaryIndex(true));

    private static AnnotationConfigApplicationContext applicationContext;

    @BeforeAll
    static void openApplicationContext() {
        applicationContext = new AnnotationConfigApplicationContext(TestConfiguration.class);
        applicationContext.getBean(Cluster.class).waitUntilReady(Duration.ofSeconds(30));
    }

    @AfterAll
    static void closeApplicationContext() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    @Test
    void repositoryProxyMapsDocumentsAndDerivesN1qlQueries() {
        PersonRepository repository = applicationContext.getBean(PersonRepository.class);
        repository.save(new Person("repository-ada", "Ada", "engineer", List.of("math", "logic")));
        repository.save(new Person("repository-grace", "Grace", "engineer", List.of("compiler")));

        Person found = repository.findById("repository-ada").orElseThrow();
        assertThat(found.getName()).isEqualTo("Ada");
        assertThat(found.getInterests()).containsExactly("math", "logic");
        assertThat(repository.findByCategory("engineer"))
                .extracting(Person::getName)
                .containsExactlyInAnyOrder("Ada", "Grace");

        repository.deleteById("repository-ada");
        assertThat(repository.findById("repository-ada")).isEmpty();
        repository.deleteById("repository-grace");
    }

    @Test
    void versionedRepositoryRejectsStaleDocumentUpdates() {
        VersionedPersonRepository repository =
                applicationContext.getBean(VersionedPersonRepository.class);
        String id = "optimistic-locking-ada";
        repository.save(new VersionedPerson(id, "Ada"));

        VersionedPerson current = repository.findById(id).orElseThrow();
        VersionedPerson stale = repository.findById(id).orElseThrow();
        current.setName("updated Ada");
        VersionedPerson updated = repository.save(current);

        assertThat(updated.getVersion()).isGreaterThan(stale.getVersion());
        stale.setName("stale Ada");
        assertThatThrownBy(() -> repository.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo("updated Ada");

        repository.deleteById(id);
    }

    @Test
    void couchbaseTemplateConvertsAndQueriesDocuments() {
        CouchbaseTemplate template = applicationContext.getBean(CouchbaseTemplate.class);
        Person saved = template.save(
                new Person("template-rene", "Rene", "researcher", List.of("maps", "data")));

        assertThat(saved.getId()).isEqualTo("template-rene");
        Person found = template.findById(Person.class).one("template-rene");
        assertThat(found.getName()).isEqualTo("Rene");
        assertThat(found.getInterests()).containsExactly("maps", "data");

        List<Person> matches = template.findByQuery(Person.class)
                .matching(Query.query(QueryCriteria.where("category").is("researcher")))
                .all();
        assertThat(matches).extracting(Person::getName).containsExactly("Rene");

        template.removeById(Person.class).one("template-rene");
        assertThat(template.findById(Person.class).one("template-rene")).isNull();
    }

    @Test
    void transactionTemplateCommitsMultipleDocumentWrites() {
        CouchbaseTemplate template = applicationContext.getBean(CouchbaseTemplate.class);
        TransactionTemplate transactionTemplate =
                applicationContext.getBean("couchbaseTransactionTemplate", TransactionTemplate.class);

        List<Person> committed = transactionTemplate.execute(status -> {
            Person ada = template.save(
                    new Person("transaction-ada", "Ada", "engineer", List.of("math")));
            Person grace = template.save(
                    new Person("transaction-grace", "Grace", "engineer", List.of("compiler")));
            return List.of(ada, grace);
        });

        assertThat(committed).extracting(Person::getName).containsExactly("Ada", "Grace");
        assertThat(template.findById(Person.class).one("transaction-ada").getCategory())
                .isEqualTo("engineer");
        assertThat(template.findById(Person.class).one("transaction-grace").getInterests())
                .containsExactly("compiler");

        template.removeById(Person.class).one("transaction-ada");
        template.removeById(Person.class).one("transaction-grace");
    }

    @Test
    void reactiveTemplateSavesAndFindsMappedDocuments() {
        ReactiveCouchbaseTemplate template =
                applicationContext.getBean(ReactiveCouchbaseTemplate.class);
        Person person = new Person("reactive-marie", "Marie", "chemist", List.of("reactions"));

        Person saved = template.save(person).block(Duration.ofSeconds(10));
        assertThat(saved.getName()).isEqualTo("Marie");

        Person found = template.findById(Person.class).one("reactive-marie")
                .block(Duration.ofSeconds(10));
        assertThat(found.getInterests()).containsExactly("reactions");

        template.removeById(Person.class)
                .one("reactive-marie")
                .block(Duration.ofSeconds(10));
        assertThat(template.findById(Person.class).one("reactive-marie")
                .block(Duration.ofSeconds(10))).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCouchbaseRepositories(
            basePackageClasses = Spring_data_couchbaseTest.class,
            considerNestedRepositories = true)
    public static class TestConfiguration extends AbstractCouchbaseConfiguration {
        @Override
        public String getConnectionString() {
            return COUCHBASE.getConnectionString();
        }

        @Override
        public String getUserName() {
            return COUCHBASE.getUsername();
        }

        @Override
        public String getPassword() {
            return COUCHBASE.getPassword();
        }

        @Override
        public String getBucketName() {
            return BUCKET;
        }

        @Override
        protected Set<Class<?>> getInitialEntitySet() {
            return Set.of(Person.class, VersionedPerson.class);
        }

        @Override
        public CouchbaseMappingContext couchbaseMappingContext(CustomConversions customConversions)
                throws Exception {
            CouchbaseMappingContext mappingContext = super.couchbaseMappingContext(customConversions);
            getInitialEntitySet().forEach(entityType -> mappingContext
                    .getRequiredPersistentEntity(entityType)
                    .setPersistentPropertyAccessorFactory(BeanWrapperPropertyAccessorFactory.INSTANCE));
            return mappingContext;
        }

        @Override
        public QueryScanConsistency getDefaultConsistency() {
            return QueryScanConsistency.REQUEST_PLUS;
        }
    }

    public interface PersonRepository extends CouchbaseRepository<Person, String> {
        List<Person> findByCategory(String category);
    }

    public interface VersionedPersonRepository extends CouchbaseRepository<VersionedPerson, String> {
    }

    @Document
    public static class VersionedPerson {
        @Id
        private String id;
        private String name;
        @Version
        private Long version;

        public VersionedPerson() {
        }

        VersionedPerson(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getVersion() {
            return version;
        }
    }

    @Document
    public static class Person {
        @Id
        private String id;
        private String name;
        private String category;
        private List<String> interests;

        public Person() {
        }

        public Person(String id, String name, String category, List<String> interests) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.interests = interests;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public List<String> getInterests() {
            return interests;
        }

        public void setInterests(List<String> interests) {
            this.interests = interests;
        }
    }
}
