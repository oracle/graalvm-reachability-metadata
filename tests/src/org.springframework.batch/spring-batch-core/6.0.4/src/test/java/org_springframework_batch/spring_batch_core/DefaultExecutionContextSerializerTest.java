/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.dao.DefaultExecutionContextSerializer;

public class DefaultExecutionContextSerializerTest {
    @Test
    void roundTripsExecutionContextValues() throws Exception {
        DefaultExecutionContextSerializer serializer = new DefaultExecutionContextSerializer();
        Map<String, Object> context = new HashMap<>();
        context.put("file", "orders.csv");
        context.put("position", 17);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        serializer.serialize(context, output);
        Map<String, Object> restored = serializer.deserialize(new ByteArrayInputStream(output.toByteArray()));

        assertEquals(context, restored);
    }
}
