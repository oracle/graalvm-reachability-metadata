/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.DescriptorProtos.Edition;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.util.ProtoFileUtil;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Descriptor edition labeling is the schema registry subsystem exercised here.
 *
 * A schema registry summarizes the language edition of each uploaded proto file
 * so clients can display compatible schema information in their catalog.
 */
public class ProtoEditionCatalogTest {

    @Test
    void summarizesUploadedProtoEditions() {
        List<FileDescriptorProto> uploadedSchemas =
                List.of(
                        FileDescriptorProto.newBuilder()
                                .setName("billing.proto")
                                .setEdition(Edition.EDITION_2023)
                                .build(),
                        FileDescriptorProto.newBuilder()
                                .setName("fulfillment.proto")
                                .setEdition(Edition.EDITION_2024)
                                .build());

        List<String> catalogEntries =
                uploadedSchemas.stream()
                        .map(
                                schema ->
                                        schema.getName()
                                                + " (edition "
                                                + ProtoFileUtil.getEditionString(schema.getEdition())
                                                + ")")
                        .toList();

        assertEquals(
                List.of(
                        "billing.proto (edition 2023)",
                        "fulfillment.proto (edition 2024)"),
                catalogEntries);
    }
}
