/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_couchbase;

import java.lang.reflect.AnnotatedElement;

import org.junit.jupiter.api.Test;

import org.springframework.data.couchbase.core.query.OptionsBuilder;
import org.springframework.data.couchbase.repository.Collection;
import org.springframework.data.couchbase.repository.Scope;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionsBuilderTest {

    @Test
    void resolvesNonDefaultAnnotationAttributes() {
        AnnotatedElement[] elements = {AnnotatedDocument.class};

        assertThat(OptionsBuilder.annotation(Collection.class, "value", "_default", elements)).isNotNull();
        assertThat(OptionsBuilder.annotation(Collection.class, "_default", elements)).isNotNull();
        assertThat(OptionsBuilder.annotationAttribute(Scope.class, "value", "_default", elements))
                .isEqualTo("sales");
        assertThat(OptionsBuilder.annotationString(Collection.class, "value", "_default", elements))
                .isEqualTo("orders");
        assertThat(OptionsBuilder.annotationString(Scope.class, "_default", elements)).isEqualTo("sales");
    }

    @Collection("orders")
    @Scope("sales")
    public static class AnnotatedDocument {
    }
}
