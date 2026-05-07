/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_protobuf;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.ListValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Value;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.protobuf.StatusProto;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Grpc_protobufTest {
    @Test
    void protoMarshallerStreamsAndParsesFullProtobufMessages() throws IOException {
        StringValue message = StringValue.newBuilder().setValue("hello from grpc-protobuf").build();
        Marshaller<StringValue> marshaller = ProtoUtils.marshaller(StringValue.getDefaultInstance());

        byte[] bytes = readAllBytes(marshaller.stream(message));
        StringValue parsedFromMarshallerStream = marshaller.parse(new ByteArrayInputStream(bytes));
        StringValue parsedFromRegularStream = marshaller.parse(new ByteArrayInputStream(message.toByteArray()));

        assertThat(parsedFromMarshallerStream).isEqualTo(message);
        assertThat(parsedFromRegularStream).isEqualTo(message);
    }

    @Test
    void protobufMessagesCanBeStoredInGrpcMetadataWithGeneratedBinaryKey() {
        StringValue value = StringValue.newBuilder().setValue("metadata payload").build();
        Metadata metadata = new Metadata();
        Metadata.Key<StringValue> key = ProtoUtils.keyForProto(StringValue.getDefaultInstance());

        metadata.put(key, value);

        assertThat(metadata.get(key)).isEqualTo(value);
        assertThat(metadata.keys()).contains(key.name());
    }

    @Test
    void metadataMarshallerRoundTripsRawHeaderBytes() {
        StringValue value = StringValue.newBuilder().setValue("raw header bytes").build();
        Metadata.BinaryMarshaller<StringValue> marshaller = ProtoUtils.metadataMarshaller(
                StringValue.getDefaultInstance());

        byte[] bytes = marshaller.toBytes(value);
        StringValue parsed = marshaller.parseBytes(bytes);

        assertThat(parsed).isEqualTo(value);
    }

    @Test
    void marshallerWithRecursionLimitParsesNestedMessagesWhenLimitIsSufficient() {
        Value nestedValue = nestedListValue(8);
        Marshaller<Value> marshaller = ProtoUtils.marshallerWithRecursionLimit(Value.getDefaultInstance(), 64);

        Value parsed = marshaller.parse(new ByteArrayInputStream(nestedValue.toByteArray()));

        assertThat(parsed).isEqualTo(nestedValue);
    }

    @Test
    void marshallerWithRecursionLimitRejectsMessagesPastConfiguredDepth() {
        Value nestedValue = nestedListValue(20);
        Marshaller<Value> marshaller = ProtoUtils.marshallerWithRecursionLimit(Value.getDefaultInstance(), 4);

        StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
                () -> marshaller.parse(new ByteArrayInputStream(nestedValue.toByteArray())));

        assertThat(exception.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INTERNAL);
    }

    @Test
    void extensionRegistryCanBeConfiguredWithoutChangingNormalParsing() {
        StringValue value = StringValue.newBuilder().setValue("uses configured registry").build();
        ProtoUtils.setExtensionRegistry(ExtensionRegistry.newInstance());

        Marshaller<StringValue> marshaller = ProtoUtils.marshaller(StringValue.getDefaultInstance());
        StringValue parsed = marshaller.parse(new ByteArrayInputStream(value.toByteArray()));

        assertThat(parsed).isEqualTo(value);
    }

    @Test
    void extensionRegistryParsesRegisteredExtensions() throws Exception {
        FileDescriptor fileDescriptor = buildExtendedMessageFileDescriptor();
        Descriptor hostDescriptor = fileDescriptor.findMessageTypeByName("Host");
        FieldDescriptor extensionField = fileDescriptor.findExtensionByName("label");
        DynamicMessage defaultInstance = DynamicMessage.getDefaultInstance(hostDescriptor);
        DynamicMessage message = DynamicMessage.newBuilder(hostDescriptor)
                .setField(extensionField, "registered extension")
                .build();
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        registry.add(extensionField);
        ProtoUtils.setExtensionRegistry(registry);

        Marshaller<DynamicMessage> marshaller = ProtoUtils.marshaller(defaultInstance);
        DynamicMessage parsed = marshaller.parse(new ByteArrayInputStream(message.toByteArray()));

        assertThat(parsed.getField(extensionField)).isEqualTo("registered extension");
        assertThat(parsed.getAllFields()).containsEntry(extensionField, "registered extension");
    }

    @Test
    void statusProtoConvertsRpcStatusToRuntimeExceptionAndBack() {
        Status rpcStatus = Status.newBuilder()
                .setCode(Code.INVALID_ARGUMENT.getNumber())
                .setMessage("invalid request")
                .build();
        Metadata.Key<byte[]> customKey = Metadata.Key.of("custom-bin", Metadata.BINARY_BYTE_MARSHALLER);
        Metadata trailers = new Metadata();
        trailers.put(customKey, new byte[] {1, 2, 3});

        StatusRuntimeException exception = StatusProto.toStatusRuntimeException(rpcStatus, trailers);

        assertThat(exception.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
        assertThat(exception.getStatus().getDescription()).isEqualTo("invalid request");
        assertThat(exception.getTrailers().get(customKey)).containsExactly(1, 2, 3);
        assertThat(StatusProto.fromThrowable(exception)).isEqualTo(rpcStatus);
        Status extractedStatus = StatusProto.fromStatusAndTrailers(exception.getStatus(), exception.getTrailers());
        assertThat(extractedStatus).isEqualTo(rpcStatus);
    }

    @Test
    void statusProtoCreatesCheckedExceptionWithTrailersAndCause() {
        Status rpcStatus = Status.newBuilder()
                .setCode(Code.NOT_FOUND.getNumber())
                .setMessage("missing resource")
                .build();
        Metadata.Key<String> textKey = Metadata.Key.of("resource-id", Metadata.ASCII_STRING_MARSHALLER);
        Metadata trailers = new Metadata();
        trailers.put(textKey, "item-123");
        IllegalStateException cause = new IllegalStateException("repository lookup failed");

        StatusException exception = StatusProto.toStatusException(rpcStatus, trailers, cause);

        assertThat(exception.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.NOT_FOUND);
        assertThat(exception.getStatus().getDescription()).isEqualTo("missing resource");
        assertThat(exception.getTrailers().get(textKey)).isEqualTo("item-123");
        assertThat(exception).hasCause(cause);
        assertThat(StatusProto.fromThrowable(exception)).isEqualTo(rpcStatus);
    }

    @Test
    void statusProtoConvertsPlainGrpcStatusWhenThrowableDoesNotContainRpcStatusDetails() {
        StatusRuntimeException exception = io.grpc.Status.UNKNOWN
                .withDescription("transport failed")
                .asRuntimeException();

        Status status = StatusProto.fromThrowable(exception);

        assertThat(status.getCode()).isEqualTo(Code.UNKNOWN.getNumber());
        assertThat(status.getMessage()).isEqualTo("transport failed");
    }

    @Test
    void descriptorSupplierInterfacesExposeFileServiceAndMethodDescriptors() throws Exception {
        FileDescriptor fileDescriptor = buildGreeterFileDescriptor();
        ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName("Greeter");
        MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName("SayHello");
        TestMethodDescriptorSupplier supplier = new TestMethodDescriptorSupplier(fileDescriptor, serviceDescriptor,
                methodDescriptor);

        assertThat(supplier.getFileDescriptor().getName()).isEqualTo("testing/greeter.proto");
        assertThat(supplier.getServiceDescriptor().getFullName()).isEqualTo("testing.Greeter");
        assertThat(supplier.getMethodDescriptor().getFullName()).isEqualTo("testing.Greeter.SayHello");
        assertThat(supplier.getMethodDescriptor().getInputType().getFullName()).isEqualTo("testing.HelloRequest");
        assertThat(supplier.getMethodDescriptor().getOutputType().getFullName()).isEqualTo("testing.HelloReply");
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (InputStream stream = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[128];
            int read = stream.read(buffer);
            while (read != -1) {
                output.write(buffer, 0, read);
                read = stream.read(buffer);
            }
            return output.toByteArray();
        }
    }

    private static Value nestedListValue(int depth) {
        Value value = Value.newBuilder().setStringValue("leaf").build();
        for (int index = 0; index < depth; index++) {
            value = Value.newBuilder()
                    .setListValue(ListValue.newBuilder().addValues(value))
                    .build();
        }
        return value;
    }

    private static FileDescriptor buildExtendedMessageFileDescriptor() throws Exception {
        DescriptorProto host = DescriptorProto.newBuilder()
                .setName("Host")
                .addExtensionRange(ExtensionRange.newBuilder().setStart(100).setEnd(101))
                .build();
        FieldDescriptorProto extension = FieldDescriptorProto.newBuilder()
                .setName("label")
                .setNumber(100)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_STRING)
                .setExtendee(".testing.Host")
                .build();
        FileDescriptorProto file = FileDescriptorProto.newBuilder()
                .setName("testing/extended-message.proto")
                .setPackage("testing")
                .addMessageType(host)
                .addExtension(extension)
                .build();
        return FileDescriptor.buildFrom(file, new FileDescriptor[0]);
    }

    private static FileDescriptor buildGreeterFileDescriptor() throws Exception {
        DescriptorProto request = DescriptorProto.newBuilder()
                .setName("HelloRequest")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("name")
                        .setNumber(1)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING))
                .build();
        DescriptorProto response = DescriptorProto.newBuilder()
                .setName("HelloReply")
                .addField(FieldDescriptorProto.newBuilder()
                        .setName("message")
                        .setNumber(1)
                        .setType(FieldDescriptorProto.Type.TYPE_STRING))
                .build();
        MethodDescriptorProto method = MethodDescriptorProto.newBuilder()
                .setName("SayHello")
                .setInputType(".testing.HelloRequest")
                .setOutputType(".testing.HelloReply")
                .build();
        ServiceDescriptorProto service = ServiceDescriptorProto.newBuilder()
                .setName("Greeter")
                .addMethod(method)
                .build();
        FileDescriptorProto file = FileDescriptorProto.newBuilder()
                .setName("testing/greeter.proto")
                .setPackage("testing")
                .addMessageType(request)
                .addMessageType(response)
                .addService(service)
                .build();
        return FileDescriptor.buildFrom(file, new FileDescriptor[0]);
    }

    private static final class TestMethodDescriptorSupplier implements ProtoMethodDescriptorSupplier {
        private final FileDescriptor fileDescriptor;
        private final ServiceDescriptor serviceDescriptor;
        private final MethodDescriptor methodDescriptor;

        private TestMethodDescriptorSupplier(FileDescriptor fileDescriptor, ServiceDescriptor serviceDescriptor,
                MethodDescriptor methodDescriptor) {
            this.fileDescriptor = fileDescriptor;
            this.serviceDescriptor = serviceDescriptor;
            this.methodDescriptor = methodDescriptor;
        }

        @Override
        public FileDescriptor getFileDescriptor() {
            return fileDescriptor;
        }

        @Override
        public ServiceDescriptor getServiceDescriptor() {
            return serviceDescriptor;
        }

        @Override
        public MethodDescriptor getMethodDescriptor() {
            return methodDescriptor;
        }
    }
}
