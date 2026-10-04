/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import org.junit.jupiter.api.Test;

import org.springframework.data.annotation.Version;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseMappingContext;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.repository.support.Util;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilTest {

    @Test
    void detectsNonZeroVersionProperty() {
        MappingCouchbaseConverter converter = new MappingCouchbaseConverter(new CouchbaseMappingContext());

        VersionedDocument document = new VersionedDocument();
        document.version = 3L;

        assertThat(Util.hasNonZeroVersionProperty(document, converter)).isTrue();
    }

    @Document
    public static class VersionedDocument {

        @Version
        public Long version;
    }
}
