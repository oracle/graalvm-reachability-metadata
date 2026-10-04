/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.annotation.Version;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.support.Util;

@Timeout(60)
public class UtilTest {

    @Test
    void detectsNonZeroVersionProperties() {
        CouchbaseMappingContext mappingContext = new CouchbaseMappingContext();
        mappingContext.getRequiredPersistentEntity(VersionedEntity.class);
        MappingCouchbaseConverter converter = new MappingCouchbaseConverter(mappingContext);
        VersionedEntity entity = new VersionedEntity();

        entity.version = 1L;
        assertThat(Util.hasNonZeroVersionProperty(entity, converter)).isTrue();
        entity.version = 0L;
        assertThat(Util.hasNonZeroVersionProperty(entity, converter)).isFalse();
    }

    @Document
    public static class VersionedEntity {
        @Version
        public Long version;
    }
}
