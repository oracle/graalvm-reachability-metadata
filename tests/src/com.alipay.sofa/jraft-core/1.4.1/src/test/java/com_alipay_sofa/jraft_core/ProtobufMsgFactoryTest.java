/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.entity.LocalStorageOutter;
import com.alipay.sofa.jraft.entity.RaftOutter;
import com.alipay.sofa.jraft.entity.codec.v2.LogOutter;
import com.alipay.sofa.jraft.rpc.CliRequests;
import com.alipay.sofa.jraft.rpc.ProtobufMsgFactory;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.storage.io.ProtoBufFile;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufMsgFactoryTest {
    @TempDir
    Path directory;

    @Test
    void parsesRpcMessagesByJavaAndProtocolNames() {
        RpcRequests.PingRequest request = RpcRequests.PingRequest.newBuilder().setSendTimestamp(12345L).build();
        ProtobufMsgFactory.load();
        Message byJavaName = ProtobufMsgFactory.newMessageByJavaClassName(
                "com.alipay.sofa.jraft.rpc.RpcRequests$PingRequest", request.toByteArray());
        Message byProtoName = ProtobufMsgFactory.newMessageByProtoClassName(
                request.getDescriptorForType().getFullName(), request.toByteArray());
        assertEquals(request, byJavaName);
        assertEquals(request, byProtoName);
    }

    @Test
    void editsAndRoundTripsAllProtocolMessageFields() {
        List<FileDescriptor> files = List.of(LocalFileMetaOutter.getDescriptor(), RaftOutter.getDescriptor(),
                LocalStorageOutter.getDescriptor(), RpcRequests.getDescriptor(), CliRequests.getDescriptor(),
                LogOutter.getDescriptor());
        for (FileDescriptor file : files) {
            assertFalse(file.getMessageTypes().isEmpty());
            for (Descriptor descriptor : file.getMessageTypes()) {
                String javaName = file.getOptions().getJavaPackage() + "."
                        + file.getOptions().getJavaOuterClassname() + "$" + descriptor.getName();
                Message prototype = ProtobufMsgFactory.getDefaultInstance(javaName);
                assertEquals(descriptor, prototype.getDescriptorForType());
                Message original = populate(prototype.newBuilderForType());
                Message parsed = ProtobufMsgFactory.newMessageByJavaClassName(javaName, original.toByteArray());
                assertEquals(original, parsed, descriptor.getFullName());
                assertEquals(original, ProtobufMsgFactory.newMessageByProtoClassName(
                        descriptor.getFullName(), original.toByteArray()));
                assertEquals(original.getAllFields(), parsed.getAllFields());
                assertEquals(original, parsed.toBuilder().build());
            }
        }
    }

    @Test
    void persistsRaftTermAndVoteThroughProtobufFile() throws Exception {
        LocalStorageOutter.StablePBMeta expected = LocalStorageOutter.StablePBMeta.newBuilder()
                .setTerm(7).setVotedfor("127.0.0.1:8081").build();
        ProtoBufFile file = new ProtoBufFile(directory.resolve("raft-meta").toString());
        assertTrue(file.save(expected, true));
        assertEquals(expected, file.load());
    }

    private static Message populate(Message.Builder builder) {
        for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
            Object value = fieldValue(builder, field);
            if (field.isRepeated()) {
                builder.addRepeatedField(field, value);
                assertEquals(1, builder.getRepeatedFieldCount(field));
                assertEquals(value, builder.getRepeatedField(field, 0));
                builder.setRepeatedField(field, 0, value);
            } else {
                builder.setField(field, value);
                assertTrue(builder.hasField(field));
                assertEquals(value, builder.getField(field));
            }
        }
        Message message = builder.build();
        assertTrue(message.isInitialized());
        for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
            if (field.isRepeated()) {
                assertEquals(1, message.getRepeatedFieldCount(field));
                assertEquals(builder.getRepeatedField(field, 0), message.getRepeatedField(field, 0));
            } else {
                assertTrue(message.hasField(field));
                assertEquals(builder.getField(field), message.getField(field));
            }
            Message.Builder cleared = message.toBuilder().clearField(field);
            if (field.isRepeated()) {
                assertEquals(0, cleared.getRepeatedFieldCount(field));
            } else {
                assertFalse(cleared.hasField(field));
            }
        }
        return message;
    }

    private static Object fieldValue(Message.Builder builder, FieldDescriptor field) {
        switch (field.getJavaType()) {
            case INT:
                return 19;
            case LONG:
                return 123456789L;
            case FLOAT:
                return 1.25F;
            case DOUBLE:
                return 2.5D;
            case BOOLEAN:
                return true;
            case STRING:
                return "raft-" + field.getName();
            case BYTE_STRING:
                return ByteString.copyFromUtf8("raft-payload");
            case ENUM:
                return field.getEnumType().getValues().get(field.getEnumType().getValues().size() - 1);
            case MESSAGE:
                return populate(builder.newBuilderForField(field));
            default:
                throw new IllegalArgumentException("Unsupported protocol field: " + field.getFullName());
        }
    }
}
