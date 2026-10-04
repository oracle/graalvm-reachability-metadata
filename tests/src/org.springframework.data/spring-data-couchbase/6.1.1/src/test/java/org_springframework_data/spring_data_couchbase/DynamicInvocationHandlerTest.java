/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.query.QueryScanConsistency;
import com.couchbase.client.java.kv.UpsertOptions;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.DynamicProxyable;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.data.mapping.model.BeanWrapperPropertyAccessorFactory;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Timeout(60)
public class DynamicInvocationHandlerTest {
    private static final String IMAGE = "couchbase/server:community-7.6.2";
    private static final String USERNAME = "Administrator";
    private static final String PASSWORD = "password";
    private static final String BUCKET = "dynamic-proxy";

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
    void repositoryProxiesRetainOptionsAndDelegateOperations() {
        PersonRepository repository = applicationContext.getBean(PersonRepository.class);
        PersonRepository scopedRepository = repository
                .withOptions(UpsertOptions.upsertOptions())
                .withScope("_default")
                .withCollection("_default");
        scopedRepository.save(new Person("dynamic-ada", "Ada"));

        assertThat(scopedRepository.findById("dynamic-ada")).get()
                .extracting(person -> person.name)
                .isEqualTo("Ada");
        scopedRepository.deleteById("dynamic-ada");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCouchbaseRepositories(
            basePackageClasses = DynamicInvocationHandlerTest.class,
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
            return Set.of(Person.class);
        }

        @Override
        public CouchbaseMappingContext couchbaseMappingContext(CustomConversions customConversions)
                throws Exception {
            CouchbaseMappingContext mappingContext = super.couchbaseMappingContext(customConversions);
            mappingContext.getRequiredPersistentEntity(Person.class)
                    .setPersistentPropertyAccessorFactory(BeanWrapperPropertyAccessorFactory.INSTANCE);
            return mappingContext;
        }

        @Override
        public QueryScanConsistency getDefaultConsistency() {
            return QueryScanConsistency.REQUEST_PLUS;
        }
    }

    public interface PersonRepository
            extends CouchbaseRepository<Person, String>, DynamicProxyable<PersonRepository> {
    }

    @Document
    public static class Person {
        @Id
        private String id;
        private String name;

        public Person() {
        }

        Person(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
