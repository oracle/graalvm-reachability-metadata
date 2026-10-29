/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.shaded.com.google.protobuf.Internal;
import org.junit.jupiter.api.Test;

public class InternalTest {
    @Test
    void resolvesGeneratedDefaultInstancesForTransportMessages() {
        Metadata defaultValue = Internal.getDefaultInstance(Metadata.class);
        assertEquals("", defaultValue.getType());
        assertEquals(0, defaultValue.getHeadersCount());
        assertEquals("ConfigQueryRequest", defaultValue.toBuilder().setType("ConfigQueryRequest").build().getType());
    }
}
