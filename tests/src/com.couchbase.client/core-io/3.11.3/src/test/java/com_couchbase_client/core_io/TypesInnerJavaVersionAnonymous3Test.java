/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypesInnerJavaVersionAnonymous3Test {
    @Test
    void exercisesPublicApi() throws Exception {
        TypeToken<Map<String, List<Integer>>> type = new TypeToken<Map<String, List<Integer>>>() { };
        assertTrue(type.toString().contains("java.lang.String"));
        assertTrue(type.toString().contains("List<java.lang.Integer>"));
    }
}
