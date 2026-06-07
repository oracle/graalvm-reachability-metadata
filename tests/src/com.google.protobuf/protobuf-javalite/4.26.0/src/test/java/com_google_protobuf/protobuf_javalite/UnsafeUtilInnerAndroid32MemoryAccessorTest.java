/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.MapEntry;
import com.google.protobuf.MapField;
import com.google.protobuf.Message;
import com.google.protobuf.WireFormat;
import com.google.protobuf.WireFormat.FieldType;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class UnsafeUtilInnerAndroid32MemoryAccessorTest {
    private static final String ANDROID_32_MEMORY_JAR = "android-libcore-memory-32.jar";

    @Test
    void generatedMessageV3MapSchemaReadsDefaultEntryThroughAndroid32Accessor() throws Exception {
        try {
            assertThat(runScenarioWithAndroid32Memory())
                    .isEqualTo("forge.protobuf.Android32AccessorMessage");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String runScenarioWithAndroid32Memory() throws Exception {
        URL[] classPath = classPathWithAndroid32MemoryFirst();
        try (URLClassLoader loader = new Android32FirstClassLoader(
                classPath, UnsafeUtilInnerAndroid32MemoryAccessorTest.class.getClassLoader())) {
            Class<?> scenarioClass = loader.loadClass(Android32Scenario.class.getName());
            @SuppressWarnings("unchecked")
            Callable<String> scenario = (Callable<String>) scenarioClass
                    .getDeclaredConstructor()
                    .newInstance();
            return scenario.call();
        }
    }

    private static URL[] classPathWithAndroid32MemoryFirst() throws Exception {
        List<URL> urls = new ArrayList<>();
        File android32MemoryJar = findClassPathFile(ANDROID_32_MEMORY_JAR);
        urls.add(android32MemoryJar.toURI().toURL());
        addMatchingClassPathEntries(urls, "protobuf-javalite-");
        addMatchingClassPathEntries(urls, "protobuf-java-");
        for (String entry : classPathEntries()) {
            File file = new File(entry);
            if (!entry.isEmpty()
                    && !file.getName().equals(ANDROID_32_MEMORY_JAR)
                    && !isPrioritizedProtobufRuntime(file)) {
                urls.add(file.toURI().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static void addMatchingClassPathEntries(List<URL> urls, String fileNamePrefix)
            throws Exception {
        for (String entry : classPathEntries()) {
            File file = new File(entry);
            if (!entry.isEmpty() && file.getName().startsWith(fileNamePrefix)) {
                urls.add(file.toURI().toURL());
            }
        }
    }

    private static boolean isPrioritizedProtobufRuntime(File file) {
        return file.getName().startsWith("protobuf-javalite-")
                || file.getName().startsWith("protobuf-java-");
    }

    private static File findClassPathFile(String fileName) {
        for (String entry : classPathEntries()) {
            File file = new File(entry);
            if (!entry.isEmpty() && file.getName().equals(fileName)) {
                return file;
            }
        }
        File fallback = new File("build/libs", fileName);
        if (fallback.isFile()) {
            return fallback;
        }
        throw new IllegalStateException("Could not find " + fileName + ".");
    }

    private static String[] classPathEntries() {
        return System.getProperty("java.class.path", "").split(File.pathSeparator);
    }

    private static final class Android32FirstClassLoader extends URLClassLoader {
        Android32FirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && shouldLoadChildFirst(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException exception) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static boolean shouldLoadChildFirst(String className) {
            String testClassName = UnsafeUtilInnerAndroid32MemoryAccessorTest.class.getName();
            return className.startsWith("com.google.protobuf.")
                    || className.equals("libcore.io.Memory")
                    || className.startsWith(testClassName);
        }
    }

    public static final class Android32Scenario implements Callable<String> {
        @Override
        public String call() throws Exception {
            GeneratedFullMessage message = new GeneratedFullMessage();

            message.mergeMapPayloadThroughGeneratedMessageV3Schema();

            if (message.getDefaultInstanceForType() != GeneratedFullMessage.getDefaultInstance()) {
                throw new AssertionError("Unexpected default instance.");
            }
            String value = message.getMetadataValue("color");
            if (!"blue".equals(value)) {
                throw new AssertionError("Unexpected parsed map value: " + value);
            }
            return message.getDescriptorForType().getFullName();
        }
    }

    @SuppressWarnings("checkstyle:MemberName")
    public static final class GeneratedFullMessage extends GeneratedMessageV3 {
        private static final Descriptor DESCRIPTOR;
        private static final FieldAccessorTable FIELD_ACCESSOR_TABLE;
        private static final GeneratedFullMessage DEFAULT_INSTANCE;

        static {
            try {
                FileDescriptor fileDescriptor = FileDescriptor.buildFrom(
                        fileDescriptorProto(), new FileDescriptor[0]);
                DESCRIPTOR = fileDescriptor.findMessageTypeByName("Android32AccessorMessage");
                FIELD_ACCESSOR_TABLE = new FieldAccessorTable(
                        DESCRIPTOR, new String[] {"Metadata"});
                DEFAULT_INSTANCE = new GeneratedFullMessage();
            } catch (DescriptorValidationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private MapField<String, String> metadata_ =
                MapField.emptyMapField(MetadataDefaultEntryHolder.defaultEntry);

        private GeneratedFullMessage() {
        }

        public static GeneratedFullMessage getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        void mergeMapPayloadThroughGeneratedMessageV3Schema() throws Exception {
            CodedInputStream input = CodedInputStream.newInstance(mapPayload("color", "blue"));
            mergeFromAndMakeImmutableInternal(input, ExtensionRegistryLite.getEmptyRegistry());
        }

        String getMetadataValue(String key) {
            return metadata_.getMap().get(key);
        }

        private static byte[] mapPayload(String key, String value) throws Exception {
            byte[] entry = mapEntryPayload(key, value);
            byte[] payload = new byte[CodedOutputStream.computeTagSize(1)
                    + CodedOutputStream.computeUInt32SizeNoTag(entry.length)
                    + entry.length];
            CodedOutputStream output = CodedOutputStream.newInstance(payload);
            output.writeTag(1, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeUInt32NoTag(entry.length);
            output.writeRawBytes(entry);
            output.checkNoSpaceLeft();
            return payload;
        }

        private static byte[] mapEntryPayload(String key, String value) throws Exception {
            int size = CodedOutputStream.computeStringSize(1, key)
                    + CodedOutputStream.computeStringSize(2, value);
            byte[] payload = new byte[size];
            CodedOutputStream output = CodedOutputStream.newInstance(payload);
            output.writeString(1, key);
            output.writeString(2, value);
            output.checkNoSpaceLeft();
            return payload;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return FIELD_ACCESSOR_TABLE;
        }

        @Override
        protected Message.Builder newBuilderForType(BuilderParent parent) {
            throw new UnsupportedOperationException("Builder is not needed by this test fixture.");
        }

        @Override
        public Message.Builder newBuilderForType() {
            throw new UnsupportedOperationException("Builder is not needed by this test fixture.");
        }

        @Override
        public Message.Builder toBuilder() {
            throw new UnsupportedOperationException("Builder is not needed by this test fixture.");
        }

        @Override
        public GeneratedFullMessage getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

        private static FileDescriptorProto fileDescriptorProto() {
            DescriptorProto metadataEntry = DescriptorProto.newBuilder()
                    .setName("MetadataEntry")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("key")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("value")
                            .setNumber(2)
                            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setType(FieldDescriptorProto.Type.TYPE_STRING))
                    .setOptions(MessageOptions.newBuilder().setMapEntry(true).build())
                    .build();
            DescriptorProto message = DescriptorProto.newBuilder()
                    .setName("Android32AccessorMessage")
                    .addField(FieldDescriptorProto.newBuilder()
                            .setName("metadata")
                            .setNumber(1)
                            .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".forge.protobuf.Android32AccessorMessage.MetadataEntry"))
                    .addNestedType(metadataEntry)
                    .build();
            return FileDescriptorProto.newBuilder()
                    .setName("unsafe_util_android32_memory_accessor_test.proto")
                    .setPackage("forge.protobuf")
                    .setSyntax("proto3")
                    .addMessageType(message)
                    .build();
        }

        public static final class MetadataDefaultEntryHolder {
            public static final MapEntry<String, String> defaultEntry = MapEntry
                    .<String, String>newDefaultInstance(
                            DESCRIPTOR.findNestedTypeByName("MetadataEntry"),
                            FieldType.STRING,
                            "",
                            FieldType.STRING,
                            "");
        }
    }
}
