/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.api.grpc.auto.Metadata;
import com.alibaba.nacos.shaded.com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.jupiter.api.Test;

public class GeneratedMessageV3Test {
    @Test
    void readsAndUpdatesMessageFieldsUsingDescriptors() {
        Metadata.Builder builder = Metadata.newBuilder();
        FieldDescriptor type = Metadata.getDescriptor().findFieldByName("type");
        builder.setField(type, "ConfigPublishRequest");
        Metadata value = builder.build();
        assertEquals("ConfigPublishRequest", value.getField(type));
        assertEquals("ConfigPublishRequest", value.getType());
        assertEquals("", value.toBuilder().clearField(type).build().getType());
    }
}
