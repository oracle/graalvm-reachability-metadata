/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassLoaderClassResolverTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertEquals(List.of("entry"), NettySerializationSupport.throughStreams(new ArrayList<>(List.of("entry"))));
        assertArrayEquals(new int[]{2, 3, 5}, (int[]) NettySerializationSupport.throughStreams(new int[]{2, 3, 5}));
    }
}
