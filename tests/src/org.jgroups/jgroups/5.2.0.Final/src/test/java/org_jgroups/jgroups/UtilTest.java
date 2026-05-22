/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.Property;
import org.jgroups.util.ByteArray;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Streamable;
import org.jgroups.util.Util;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilTest {
    @Test
    void serializesSerializableObjectsThroughBuffersAndStreams() throws Exception {
        SampleSerializable expected = new SampleSerializable("jgroups", 520);

        byte[] bytes = Util.objectToByteBuffer(expected);
        assertThat((SampleSerializable) Util.objectFromByteBuffer(bytes)).isEqualTo(expected);
        SampleSerializable fromByteBuffer = Util.objectFromByteBuffer(ByteBuffer.wrap(bytes),
                UtilTest.class.getClassLoader());
        assertThat(fromByteBuffer).isEqualTo(expected);

        ByteArrayDataOutputStream out = new ByteArrayDataOutputStream(256, true);
        Util.objectToStream(expected, out);
        ByteArrayDataInputStream in = new ByteArrayDataInputStream(out.buffer(), 0, out.position());
        SampleSerializable fromStream = Util.objectFromStream(in, UtilTest.class.getClassLoader());
        assertThat(fromStream).isEqualTo(expected);
    }

    @Test
    void serializesStreamableObjectsThroughSupportedFactories() throws Exception {
        SampleStreamable expected = new SampleStreamable("alpha", 7);

        byte[] genericBytes = Util.objectToByteBuffer(expected);
        SampleStreamable generic = Util.objectFromByteBuffer(genericBytes, 0, genericBytes.length,
                UtilTest.class.getClassLoader());
        assertThat(generic).isEqualTo(expected);

        byte[] streamableBytes = Util.streamableToByteBuffer(expected);
        SampleStreamable fromBytes = Util.streamableFromByteBuffer(SampleStreamable.class, streamableBytes);
        assertThat(fromBytes).isEqualTo(expected);
        SampleStreamable fromByteBuffer = Util.streamableFromByteBuffer(SampleStreamable.class,
                ByteBuffer.wrap(streamableBytes));
        assertThat(fromByteBuffer).isEqualTo(expected);

        SampleStreamable[] values = {new SampleStreamable("one", 1), new SampleStreamable("two", 2)};
        ByteArrayDataOutputStream out = new ByteArrayDataOutputStream(256, true);
        Util.write(values, out);
        SampleStreamable[] restored = Util.read(SampleStreamable.class,
                new ByteArrayDataInputStream(out.buffer(), 0, out.position()));
        assertThat(restored).containsExactly(values);
    }

    @Test
    void serializesExceptionsUsingAvailableConstructors() throws Exception {
        IllegalArgumentException expected = new IllegalArgumentException("with-message",
                new IllegalStateException("cause"));
        ByteArray withMessage = Util.exceptionToBuffer(expected);
        Throwable restored = Util.exceptionFromBuffer(withMessage.getArray(), withMessage.getOffset(),
                withMessage.getLength());
        assertThat(restored).isInstanceOf(IllegalArgumentException.class);
        assertThat(restored).hasMessage("with-message");
        assertThat(restored.getCause()).isInstanceOf(IllegalStateException.class);

        NoMessageThrowable noMessage = new NoMessageThrowable();
        ByteArray noMessageBytes = Util.exceptionToBuffer(noMessage);
        Throwable restoredNoMessage = Util.exceptionFromBuffer(noMessageBytes.getArray(), noMessageBytes.getOffset(),
                noMessageBytes.getLength());
        assertThat(restoredNoMessage).isInstanceOf(NoMessageThrowable.class);
    }

    @Test
    void accessesFieldsMethodsAndArraysThroughUtilityApi() throws Exception {
        AnnotatedReflectionTarget target = new AnnotatedReflectionTarget();

        Field field = Util.getField(AnnotatedReflectionTarget.class, "secret", true);
        Util.setField(field, target, "changed");
        assertThat(Util.getField(field, target)).isEqualTo("changed");
        assertThat(Util.findField(target, List.of("missing", "secret"))).isEqualTo(field);

        Field[] propertyFields = Util.getAllDeclaredFieldsWithAnnotations(AnnotatedReflectionTarget.class,
                Property.class);
        assertThat(Arrays.stream(propertyFields).map(Field::getName)).contains("secret");

        Method[] managedMethods = Util.getAllDeclaredMethodsWithAnnotations(AnnotatedReflectionTarget.class,
                ManagedAttribute.class);
        assertThat(Arrays.stream(managedMethods).map(Method::getName)).contains("managedValue");
        assertThat(Util.getAllMethods(AnnotatedReflectionTarget.class)).extracting(Method::getName)
                .contains("managedValue", "defaultMethod");
        assertThat(Util.findMethod(AnnotatedReflectionTarget.class, "setNumber", new Object[] {"11"})).isNotNull();
        assertThat(Util.findMethod(target, List.of("missing", "setNumber"), int.class)).isNotNull();
        assertThat(Util.findMethod(AnnotatedReflectionTarget.class, "defaultMethod")).isNotNull();

        String[] combined = Util.combine(new String[] {"a"}, new String[] {"b", "c"});
        assertThat(combined).containsExactly("a", "b", "c");
    }

    @Test
    void loadsClassesResourcesAndCustomAddressSupplier() throws Exception {
        assertThat(Util.loadClass(String.class.getName(), UtilTest.class)).isSameAs(String.class);
        InetAddress customAddress = Util.getAddressByCustomCode(CustomInetAddressSupplier.class.getName());
        assertThat(customAddress).isEqualTo(InetAddress.getLoopbackAddress());

        Path root = Files.createTempDirectory("jgroups-util-resources");
        try {
            Path packageDirectory = Files.createDirectories(root.resolve("org_jgroups/jgroups"));
            Files.createFile(packageDirectory.resolve("UtilAssignableTarget.class"));
            Files.createFile(packageDirectory.resolve("UtilAnnotatedTarget.class"));
            Files.writeString(root.resolve("context-resource.txt"), "context", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("loader-resource.txt"), "loader", StandardCharsets.UTF_8);

            URL[] urls = {root.toUri().toURL()};
            try (URLClassLoader loader = new URLClassLoader(urls, null)) {
                Set<Class<?>> assignable = Util.findClassesAssignableFromPath("org_jgroups/jgroups", UtilMarker.class,
                        loader);
                assertThat(assignable).contains(UtilAssignableTarget.class);

                ClassLoader original = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(loader);
                try {
                    assertThat(Util.findClassesAnnotatedWith("org_jgroups.jgroups", Deprecated.class))
                            .contains(UtilAnnotatedTarget.class);
                    try (InputStream stream = Util.getResourceAsStream("context-resource.txt", (ClassLoader) null)) {
                        assertThat(stream).isNotNull();
                        assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("context");
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(original);
                }

                try (InputStream stream = Util.getResourceAsStream("loader-resource.txt", loader)) {
                    assertThat(stream).isNotNull();
                    assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("loader");
                }
            }
        } finally {
            deleteRecursively(root);
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader emptyLoader = new URLClassLoader(new URL[0], null)) {
            Thread.currentThread().setContextClassLoader(emptyLoader);
            try (InputStream stream = Util.getResourceAsStream("jg-messages.properties", (ClassLoader) null)) {
                assertThat(stream).isNotNull();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    public static class CustomInetAddressSupplier implements Supplier<InetAddress> {
        public CustomInetAddressSupplier() {
        }

        @Override
        public InetAddress get() {
            return InetAddress.getLoopbackAddress();
        }
    }

    public static class NoMessageThrowable extends Throwable {
        private static final long serialVersionUID = 1L;

        public NoMessageThrowable() {
        }
    }

    public static class SampleStreamable implements Streamable {
        private String name;
        private int value;

        public SampleStreamable() {
        }

        SampleStreamable(String newName, int newValue) {
            name = newName;
            value = newValue;
        }

        @Override
        public void writeTo(DataOutput out) throws IOException {
            out.writeUTF(name);
            out.writeInt(value);
        }

        @Override
        public void readFrom(DataInput in) throws IOException {
            name = in.readUTF();
            value = in.readInt();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SampleStreamable)) {
                return false;
            }
            SampleStreamable that = (SampleStreamable) other;
            return value == that.value && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + value;
        }
    }
}

class SampleSerializable implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private int value;

    SampleSerializable(String newName, int newValue) {
        name = newName;
        value = newValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SampleSerializable)) {
            return false;
        }
        SampleSerializable that = (SampleSerializable) other;
        return value == that.value && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + value;
    }
}


interface UtilMarker {
}

class UtilAssignableTarget implements UtilMarker {
}

@Deprecated
class UtilAnnotatedTarget {
}

interface UtilDefaultMethod {
    default String defaultMethod() {
        return "default";
    }
}

class AnnotatedReflectionParent {
    @Property
    private String parentSecret = "parent";
}

class AnnotatedReflectionTarget extends AnnotatedReflectionParent implements UtilDefaultMethod {
    @Property
    private String secret = "initial";
    private int number;

    public void setNumber(int newNumber) {
        number = newNumber;
    }

    @ManagedAttribute
    public int managedValue() {
        return number;
    }
}
