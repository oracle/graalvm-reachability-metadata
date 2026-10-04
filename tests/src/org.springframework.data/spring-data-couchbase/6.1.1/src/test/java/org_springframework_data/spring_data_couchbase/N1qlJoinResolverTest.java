/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.couchbase.core.convert.join.N1qlJoinResolver;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.query.FetchType;
import org.springframework.data.couchbase.core.query.N1qlJoin;

@Timeout(60)
public class N1qlJoinResolverTest {

    @Test
    void recognizesLazyJoinDefinitions() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        N1qlJoin join = mappingContext.getRequiredPersistentEntity(Joined.class)
                .getRequiredPersistentProperty("related")
                .findAnnotation(N1qlJoin.class);

        assertThat(N1qlJoinResolver.isLazyJoin(join)).isTrue();
    }

    @Document
    private static class Joined {
        @N1qlJoin(on = "lks.key = rks.key", fetchType = FetchType.LAZY)
        private List<Related> related;
    }

    private static class Related {
        private String key;
    }
}
