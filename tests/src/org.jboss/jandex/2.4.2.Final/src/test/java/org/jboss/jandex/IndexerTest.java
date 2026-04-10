/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.jandex;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexerTest {

    @Test
    void indexesLoadedClassViaItsClassResource() throws Exception {
        Indexer indexer = new Indexer();

        ClassInfo classInfo = indexer.indexClass(IndexerIndexClassTarget.class);

        assertThat(classInfo).isNotNull();
        assertThat(classInfo.name().toString()).isEqualTo(IndexerIndexClassTarget.class.getName());
        assertThat(classInfo.classAnnotation(DotName.createSimple(Deprecated.class.getName()))).isNotNull();
    }
}

@Deprecated
final class IndexerIndexClassTarget {
}
