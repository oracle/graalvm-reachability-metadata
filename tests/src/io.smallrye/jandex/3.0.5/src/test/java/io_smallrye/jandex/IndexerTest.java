/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

public class IndexerTest {
    @Test
    void indexesLoadedClassFromItsClassResource() throws IOException {
        Indexer indexer = new Indexer();

        indexer.indexClass(IndexedSample.class);
        Index index = indexer.complete();
        ClassInfo classInfo = index.getClassByName(IndexedSample.class);

        assertThat(classInfo).isNotNull();
        assertThat(classInfo.name().toString()).isEqualTo(IndexedSample.class.getName());
        assertThat(classInfo.classAnnotation(DotName.createSimple(SampleAnnotation.class.getName()))).isNotNull();
        assertThat(classInfo.field("name")).isNotNull();
        assertThat(classInfo.firstMethod("value")).isNotNull();
    }

    @SampleAnnotation("sample")
    static class IndexedSample {
        String name = "sample";

        String value() {
            return name;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface SampleAnnotation {
        String value();
    }
}
