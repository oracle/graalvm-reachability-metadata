/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.core.convert.join.N1qlJoinResolver;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.CouchbasePersistentProperty;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.FetchType;
import org.springframework.data.couchbase.core.query.N1qlJoin;

import static org.assertj.core.api.Assertions.assertThat;

public class N1qlJoinResolverInnerN1qlJoinProxyTest {

    @Test
    void identifiesLazyJoinProperties() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        CouchbasePersistentProperty property = mappingContext.getPersistentEntity(JoinDocument.class)
                .getRequiredPersistentProperty("joined");

        assertThat(N1qlJoinResolver.isLazyJoin(property.findAnnotation(N1qlJoin.class))).isTrue();
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
