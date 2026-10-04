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
import java.time.Duration;
import java.util.List;
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
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.FetchType;
import org.springframework.data.couchbase.core.query.N1qlJoin;
import org.springframework.data.mapping.model.BeanWrapperPropertyAccessorFactory;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Timeout(60)
public class N1qlJoinResolverInnerN1qlJoinProxyTest {
    private static final String IMAGE = "couchbase/server:community-7.6.2";
    private static final String USERNAME = "Administrator";
    private static final String PASSWORD = "password";
    private static final String BUCKET = "n1ql-join";

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
    void lazilyLoadsAssociatedDocumentsThroughJoinProxy() {
        ReactiveCouchbaseTemplate template = applicationContext.getBean(ReactiveCouchbaseTemplate.class);
        template.save(new Right("right-ada", "ada-key", "Ada's profile"))
                .block(Duration.ofSeconds(10));
        template.save(new Left("left-ada", "ada-key"))
                .block(Duration.ofSeconds(10));

        Left left = template.findById(Left.class)
                .one("left-ada")
                .block(Duration.ofSeconds(10));

        assertThat(left).isNotNull();
        assertThat(left.getRelated()).extracting(Right::getName)
                .containsExactly("Ada's profile");

        template.removeById(Left.class).one("left-ada").block(Duration.ofSeconds(10));
        template.removeById(Right.class).one("right-ada").block(Duration.ofSeconds(10));
    }

    @Configuration(proxyBeanMethods = false)
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
            return Set.of(Left.class, Right.class);
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

    @Document
    public static class Left {
        @Id
        private String id;
        private String rightKey;
        @N1qlJoin(on = "lks.rightKey = rks.lookupKey", fetchType = FetchType.LAZY)
        private List<Right> related;

        public Left() {
        }

        Left(String id, String rightKey) {
            this.id = id;
            this.rightKey = rightKey;
        }

        public List<Right> getRelated() {
            return related;
        }

        public void setRelated(List<Right> related) {
            this.related = related;
        }
    }

    @Document
    public static class Right {
        @Id
        private String id;
        private String lookupKey;
        private String name;

        public Right() {
        }

        Right(String id, String lookupKey, String name) {
            this.id = id;
            this.lookupKey = lookupKey;
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
