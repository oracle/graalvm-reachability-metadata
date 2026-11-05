/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.protobuf.DescriptorProtos;
import com.couchbase.client.core.deps.com.google.protobuf.DescriptorProtos.FeatureSet;
import com.couchbase.client.core.deps.com.google.protobuf.ExtensionRegistryLite;
import com.couchbase.client.core.deps.com.google.protobuf.JavaFeaturesProto.JavaFeatures;
import com.couchbase.client.core.deps.com.google.protobuf.JavaFeaturesProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtensionRegistryFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        DescriptorProtos.getDescriptor();
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        registry.add(JavaFeaturesProto.java_);
        FeatureSet original = FeatureSet.newBuilder()
                .setExtension(JavaFeaturesProto.java_, JavaFeatures.newBuilder().setLegacyClosedEnum(true).build()).build();
        FeatureSet restored = FeatureSet.parseFrom(original.toByteArray(), registry);
        assertTrue(restored.hasExtension(JavaFeaturesProto.java_));
        assertTrue(restored.getExtension(JavaFeaturesProto.java_).getLegacyClosedEnum());
    }
}
