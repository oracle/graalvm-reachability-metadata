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

import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.data.couchbase.core.ReactiveCouchbaseTemplate;
import org.springframework.data.couchbase.core.ReactiveFindByQueryOperation.ReactiveFindByQuery;
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

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        ReactiveFindByQuery<JoinedDocument> query = mock(ReactiveFindByQuery.class, RETURNS_DEEP_STUBS);
        when(query.matching(any(Query.class)).all()).thenReturn(Flux.just(new JoinedDocument()));
        ReactiveCouchbaseTemplate template = new QueryingReactiveCouchbaseTemplate(
                mock(CouchbaseClientFactory.class), converter, query);

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
