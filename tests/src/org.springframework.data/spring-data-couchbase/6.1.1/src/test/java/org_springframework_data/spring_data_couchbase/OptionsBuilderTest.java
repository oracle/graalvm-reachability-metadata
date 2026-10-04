/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.AnnotatedElement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.couchbase.core.query.OptionsBuilder;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

@Timeout(60)
public class OptionsBuilderTest {

    @Test
    void readsNonDefaultRepositoryAnnotationAttributes() {
        AnnotatedElement[] elements = {AnnotatedDocument.class};

        assertThat(OptionsBuilder.annotationAttribute(Scope.class, "value", "_default", elements))
                .isEqualTo("tenant");
        assertThat(OptionsBuilder.annotationString(Collection.class, "value", "_default", elements))
                .isEqualTo("records");
    }

    @Scope("tenant")
    @Collection("records")
    private static class AnnotatedDocument {
    }
}
