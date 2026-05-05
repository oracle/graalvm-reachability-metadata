/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

public class IndexerTest {
    @Test
    void indexClassReadsLoadedClassResource() throws Exception {
        Indexer indexer = new Indexer();

        indexer.indexClass(IndexerTest.class);
        Index index = indexer.complete();

        ClassInfo classInfo = index.getClassByName(IndexerTest.class);
        assertNotNull(classInfo);
        assertEquals(IndexerTest.class.getName(), classInfo.name().toString());
    }
}
