/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_couchbase_test;

import java.time.Duration;
import java.util.List;

import com.couchbase.client.java.query.QueryScanConsistency;
import org.junit.jupiter.api.Test;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.couchbase.test.autoconfigure.DataCouchbaseTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.Query;
import org.springframework.data.couchbase.core.query.QueryCriteria;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration(classes = Spring_boot_data_couchbase_testTest.TestApplication.class)
@DataCouchbaseTest(properties = {
        "spring.couchbase.env.timeouts.connect=10s",
        "spring.couchbase.env.timeouts.key-value=10s",
        "spring.couchbase.env.timeouts.query=10s",
        "spring.data.couchbase.bucket-name=test-bucket"
}, includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = IncludedComponent.class))
public class Spring_boot_data_couchbase_testTest {

    @Autowired
    private CouchbaseTemplate couchbaseTemplate;

    @Autowired
    private IncludedComponent includedComponent;

    @Autowired
    private PersonRepository personRepository;

    @Test
    void includesComponentsSelectedByTheDataCouchbaseSlice() {
        assertThat(this.includedComponent.value()).isEqualTo("included");
    }

    @Test
    void storesFindsAndQueriesDocumentsThroughTheDataCouchbaseSlice() {
        Person ada = this.couchbaseTemplate.save(new Person("ada", "Ada", "software-engineer"));
        this.couchbaseTemplate.save(new Person("grace", "Grace", "scientist"));

        Person found = this.couchbaseTemplate.findById(Person.class).one(ada.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Ada");
        assertThat(found.getOccupation()).isEqualTo("software-engineer");

        List<Person> engineers = this.couchbaseTemplate.findByQuery(Person.class)
                .matching(Query.query(QueryCriteria.where("occupation").is("software-engineer"))
                        .scanConsistency(QueryScanConsistency.REQUEST_PLUS))
                .all();
        assertThat(engineers).extracting(Person::getId).containsExactly("ada");
    }

    @Test
    void persistsAndRetrievesDocumentsThroughTheAutoConfiguredRepository() {
        Person grace = this.personRepository.save(
                new Person("grace-repository", "Grace", "scientist"));

        assertThat(this.personRepository.findById(grace.getId()))
                .hasValueSatisfying(found -> assertThat(found.getName()).isEqualTo("Grace"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = IncludedComponent.class)
    @Import(CouchbaseConfiguration.class)
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CouchbaseConfiguration {

        @Bean(destroyMethod = "")
        @ServiceConnection
        CouchbaseContainer couchbaseContainer() {
            return new CouchbaseContainer("couchbase/server:7.6.2")
                    .withCredentials("Administrator", "password")
                    .withBucket(new BucketDefinition("test-bucket").withPrimaryIndex(true))
                    .withStartupTimeout(Duration.ofSeconds(50));
        }

    }

    @Document
    public static class Person {

        @Id
        private String id;

        private String name;

        private String occupation;

        public Person() {
        }

        Person(String id, String name, String occupation) {
            this.id = id;
            this.name = name;
            this.occupation = occupation;
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public String getOccupation() {
            return this.occupation;
        }

    }

}

interface PersonRepository
        extends CouchbaseRepository<Spring_boot_data_couchbase_testTest.Person, String> {
}

@Component
class IncludedComponent {

    public String value() {
        return "included";
    }

}
