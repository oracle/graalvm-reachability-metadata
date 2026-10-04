/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.support.PersistenceExceptionTranslator;

import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.FindByQueryConsistentWith;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.FindByQueryInCollection;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.FindByQueryInScope;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.FindByQueryWithConsistency;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.FindByQueryWithOptions;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.FindByQueryWithProjection;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.ReactiveFindByQuery;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.TerminatingFindByQuery;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.convert.join.N1qlJoinResolver;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentEntity;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentProperty;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.FetchType;
import org.springframework.data.couchbase.core.query.N1qlJoin;
import org.springframework.data.couchbase.core.query.Query;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.model.BeanWrapperPropertyAccessorFactory;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

public class N1qlJoinResolverInnerN1qlJoinProxyTest {

    @Test
    void resolvesLazyJoinWhenAListMethodIsCalled() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(JoinDocument.class);
        JoinDocument document = new JoinDocument();
        PersistentPropertyAccessor<JoinDocument> propertyAccessor = BeanWrapperPropertyAccessorFactory.INSTANCE
                .getPropertyAccessor(entity, document);
        ConvertingPropertyAccessor<JoinDocument> accessor = new ConvertingPropertyAccessor<>(propertyAccessor,
                DefaultConversionService.getSharedInstance());

        MappingCouchbaseConverter converter = new MappingCouchbaseConverter(mappingContext);
        converter.afterPropertiesSet();
        ReactiveFindByQuery<JoinedDocument> query = new FixedReactiveFindByQuery();
        ReactiveCouchbaseTemplate template = new QueryingReactiveCouchbaseTemplate(
                new DisconnectedClientFactory(), converter, query);

        N1qlJoinResolver.handleProperties(entity, accessor, template, "document-id", "sales", "orders");

        assertThat(document.joined).hasSize(1);
        assertThat(document.joined.get(0)).isInstanceOf(JoinedDocument.class);
    }

    @Test
    void identifiesLazyJoinProperties() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentProperty property = mappingContext.getPersistentEntity(JoinDocument.class)
                .getRequiredPersistentProperty("joined");

        assertThat(N1qlJoinResolver.isLazyJoin(property.findAnnotation(N1qlJoin.class))).isTrue();
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

    @SuppressWarnings("unchecked")
    private static class FixedReactiveFindByQuery implements ReactiveFindByQuery<JoinedDocument> {

        @Override
        public TerminatingFindByQuery<JoinedDocument> matching(Query query) {
            return this;
        }

        @Override
        public TerminatingFindByQuery<JoinedDocument> withOptions(QueryOptions options) {
            return this;
        }

        @Override
        public FindByQueryWithOptions<JoinedDocument> inCollection(String collection) {
            return this;
        }

        @Override
        public FindByQueryInCollection<JoinedDocument> inScope(String scope) {
            return this;
        }

        @Override
        public FindByQueryInScope<JoinedDocument> consistentWith(QueryScanConsistency scanConsistency) {
            return this;
        }

        @Override
        public FindByQueryConsistentWith<JoinedDocument> withConsistency(QueryScanConsistency scanConsistency) {
            return this;
        }

        @Override
        public <R> FindByQueryWithConsistency<R> as(Class<R> returnType) {
            return (FindByQueryWithConsistency<R>) this;
        }

        @Override
        public FindByQueryWithProjection<JoinedDocument> project(String[] fields) {
            return this;
        }

        @Override
        public FindByQueryWithProjection<JoinedDocument> distinct(String[] distinctFields) {
            return this;
        }

        @Override
        public Mono<JoinedDocument> one() {
            return Mono.just(new JoinedDocument());
        }

        @Override
        public Mono<JoinedDocument> first() {
            return Mono.just(new JoinedDocument());
        }

        @Override
        public Flux<JoinedDocument> all() {
            return Flux.just(new JoinedDocument());
        }

        @Override
        public Mono<Long> count() {
            return Mono.just(1L);
        }

        @Override
        public Mono<Boolean> exists() {
            return Mono.just(true);
        }
    }

    private static class QueryingReactiveCouchbaseTemplate extends ReactiveCouchbaseTemplate {

        private final ReactiveFindByQuery<JoinedDocument> query;

        QueryingReactiveCouchbaseTemplate(CouchbaseClientFactory clientFactory, MappingCouchbaseConverter converter,
                ReactiveFindByQuery<JoinedDocument> query) {
            super(clientFactory, converter);
            this.query = query;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReactiveFindByQuery<T> findByQuery(Class<T> domainType) {
            return (ReactiveFindByQuery<T>) query;
        }
    }

    @Document
    public static class JoinDocument {

        @N1qlJoin(on = "rks.documentId", fetchType = FetchType.LAZY)
        public List<JoinedDocument> joined;
    }

    @Document
    public static class JoinedDocument {

        public String documentId;
    }
}
