/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ObjectArraysTest {
    @Test
    void exercisesPublicApi() throws Exception {
        String[] values = ObjectArrays.newArray(String.class, 2);
        values[0] = "alpha";
        values[1] = "beta";
        assertArrayEquals(new String[]{"alpha", "beta", "gamma"}, ObjectArrays.concat(values, "gamma"));
        assertArrayEquals(new String[]{"alpha", "beta"}, values);
    }
}
