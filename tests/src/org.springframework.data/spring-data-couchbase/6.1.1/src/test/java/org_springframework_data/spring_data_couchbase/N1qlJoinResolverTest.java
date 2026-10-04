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
import org.springframework.data.couchbase.core.convert.join.N1qlJoinResolver;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentEntity;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.FetchType;
import org.springframework.data.couchbase.core.query.N1qlJoin;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.model.BeanWrapperPropertyAccessorFactory;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;

import static org.assertj.core.api.Assertions.assertThat;

public class N1qlJoinResolverTest {

    @Test
    void installsLazyJoinProxyWithoutConnectingToCouchbase() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentEntity<?> entity = mappingContext.getPersistentEntity(JoinDocument.class);
        JoinDocument document = new JoinDocument();
        PersistentPropertyAccessor<JoinDocument> propertyAccessor = BeanWrapperPropertyAccessorFactory.INSTANCE
                .getPropertyAccessor(entity, document);
        ConvertingPropertyAccessor<JoinDocument> accessor = new ConvertingPropertyAccessor<>(propertyAccessor,
                DefaultConversionService.getSharedInstance());

        N1qlJoinResolver.handleProperties(entity, accessor, null, "document-id", "sales", "orders");

        assertThat(document.joined).isNotNull();
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
