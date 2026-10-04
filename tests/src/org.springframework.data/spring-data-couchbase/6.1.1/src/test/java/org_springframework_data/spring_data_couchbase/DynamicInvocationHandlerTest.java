/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import org.junit.jupiter.api.Test;

import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentEntity;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.DynamicProxyable;
import org.springframework.data.couchbase.repository.support.DynamicInvocationHandler;
import org.springframework.data.couchbase.repository.support.MappingCouchbaseEntityInformation;
import org.springframework.data.couchbase.repository.support.SimpleCouchbaseRepository;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DynamicInvocationHandlerTest {

    @Test
    void createsScopedCollectionAndOptionProxiesAndInvokesRepositoryMethods() {
        TestRepository repository = repository();

        TestRepository scoped = repository.withScope("sales");
        TestRepository withOptions = scoped.withOptions(null);
        TestRepository withCollection = scoped.withCollection("orders");
        TestRepository withAnotherScope = scoped.withScope("marketing");

        assertThat(withOptions).isNotSameAs(repository);
        assertThat(withCollection).isNotSameAs(repository);
        assertThat(withAnotherScope).isNotSameAs(repository);
        assertThat(scoped.getEntityInformation()).isSameAs(repository.getEntityInformation());
    }

    @Test
    void rejectsTargetsThatAreNotCouchbaseRepositories() {
        assertThatThrownBy(() -> new DynamicInvocationHandler<>(new Object(), null, null, null))
                .isInstanceOf(RuntimeException.class);
    }

    static TestRepository repository() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        mappingContext.setAutoIndexCreation(false);
        MappingCouchbaseConverter converter = new MappingCouchbaseConverter(mappingContext);
        converter.afterPropertiesSet();
        CouchbaseTemplate template = new CouchbaseTemplate(new DisconnectedClientFactory(), converter);
        CouchbasePersistentEntity<RepositoryEntity> entity = (CouchbasePersistentEntity<RepositoryEntity>) mappingContext
                .getRequiredPersistentEntity(RepositoryEntity.class);
        MappingCouchbaseEntityInformation<RepositoryEntity, String> entityInformation =
                new MappingCouchbaseEntityInformation<>(entity);
        return new TestRepositoryImplementation(entityInformation, template);
    }

    private static class DisconnectedClientFactory implements CouchbaseClientFactory {

        @Override
        public Cluster getCluster() {
            return null;
        }

        @Override
        public Bucket getBucket() {
            return null;
        }

        @Override
        public Scope getScope() {
            return null;
        }

        @Override
        public Collection getCollection(String name) {
            return null;
        }

        @Override
        public Collection getDefaultCollection() {
            return null;
        }

        @Override
        public CouchbaseClientFactory withScope(String scopeName) {
            return this;
        }

        @Override
        public PersistenceExceptionTranslator getExceptionTranslator() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    public interface TestRepository
            extends CouchbaseRepository<RepositoryEntity, String>, DynamicProxyable<TestRepository> {
    }

    public static class TestRepositoryImplementation extends SimpleCouchbaseRepository<RepositoryEntity, String>
            implements TestRepository {

        TestRepositoryImplementation(MappingCouchbaseEntityInformation<RepositoryEntity, String> entityInformation,
                CouchbaseTemplate template) {
            super(entityInformation, template, TestRepository.class);
        }
    }

    @Document
    public static class RepositoryEntity {

        @Id
        public String id;
    }
}
