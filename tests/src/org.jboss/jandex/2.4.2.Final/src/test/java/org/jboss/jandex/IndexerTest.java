/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.jandex;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexerTest {
    @Test
    void indexClassIndexesClassMetadataViaClassResource() throws IOException {
        Indexer indexer = new Indexer();

        ClassInfo classInfo = indexer.indexClass(AnnotatedSample.class);
        Index index = indexer.complete();

        DotName sampleName = DotName.createSimple(AnnotatedSample.class.getName());
        DotName annotationName = DotName.createSimple(IndexedAnnotation.class.getName());

        assertThat(classInfo.name()).isEqualTo(sampleName);
        assertThat(classInfo.superName()).isEqualTo(DotName.OBJECT_NAME);
        assertThat(classInfo.classAnnotation(annotationName)).isNotNull();
        assertThat(classInfo.classAnnotation(annotationName).value().asString()).isEqualTo("class-level");
        assertThat(classInfo.field("value")).isNotNull();
        assertThat(classInfo.field("value").annotation(annotationName).value().asString()).isEqualTo("field-level");
        assertThat(classInfo.method("work")).isNotNull();
        assertThat(classInfo.method("work").annotation(annotationName).value().asString()).isEqualTo("method-level");
        assertThat(index.getClassByName(sampleName)).isEqualTo(classInfo);
    }

    @Test
    void indexClassRejectsNullClass() {
        Indexer indexer = new Indexer();

        assertThatThrownBy(() -> indexer.indexClass(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clazz cannot be null");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
    @interface IndexedAnnotation {
        String value();
    }

    @IndexedAnnotation("class-level")
    static class AnnotatedSample {
        @IndexedAnnotation("field-level")
        String value;

        @IndexedAnnotation("method-level")
        String work() {
            return value;
        }
    }
}
