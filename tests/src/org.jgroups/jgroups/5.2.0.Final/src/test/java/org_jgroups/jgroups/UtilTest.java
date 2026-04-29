/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.Property;
import org.jgroups.util.ByteArray;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Streamable;
import org.jgroups.util.Util;
import org.junit.jupiter.api.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilTest {
    private static final String TEST_RESOURCE = "org_jgroups/jgroups/util-test-resource.txt";

    @Test
    void serializesSerializableObjectsThroughByteBuffersAndStreams() throws Exception {
        SerializablePayload payload = new SerializablePayload("alpha", 7);

        byte[] bytes = Util.objectToByteBuffer(payload);
        assertThat((SerializablePayload)Util.objectFromByteBuffer(bytes)).isEqualTo(payload);
        assertThat((SerializablePayload)Util.objectFromByteBuffer(ByteBuffer.wrap(bytes), null)).isEqualTo(payload);

        ByteArrayDataOutputStream output = new ByteArrayDataOutputStream(128, true);
        Util.objectToStream(payload, output);
        ByteArray buffer = output.getBuffer();
        ByteArrayDataInputStream input = new ByteArrayDataInputStream(buffer.getArray(), buffer.getOffset(),
            buffer.getLength());
        assertThat((SerializablePayload)Util.objectFromStream(input, null)).isEqualTo(payload);
    }

    @Test
    void createsStreamablesAndStreamableArraysFromSerializedData() throws Exception {
        ProbeStreamable original = new ProbeStreamable("bravo", 11);

        byte[] streamableBytes = Util.streamableToByteBuffer(original);
        ProbeStreamable fromByteArray = Util.streamableFromByteBuffer(ProbeStreamable.class, streamableBytes);
        ProbeStreamable fromByteBuffer = Util.streamableFromByteBuffer(ProbeStreamable.class,
            ByteBuffer.wrap(streamableBytes));
        assertThat(fromByteArray).isEqualTo(original);
        assertThat(fromByteBuffer).isEqualTo(original);

        byte[] genericBytes = Util.objectToByteBuffer(original);
        assertThat((ProbeStreamable)Util.objectFromByteBuffer(genericBytes)).isEqualTo(original);

        ByteArrayDataOutputStream output = new ByteArrayDataOutputStream(128, true);
        Util.write(new ProbeStreamable[] {new ProbeStreamable("charlie", 1), new ProbeStreamable("delta", 2)}, output);
        ByteArray arrayBuffer = output.getBuffer();
        ByteArrayDataInputStream input = new ByteArrayDataInputStream(arrayBuffer.getArray(), arrayBuffer.getOffset(),
            arrayBuffer.getLength());

        ProbeStreamable[] result = Util.read(ProbeStreamable.class, input);
        assertThat(result).containsExactly(new ProbeStreamable("charlie", 1), new ProbeStreamable("delta", 2));
    }

    @Test
    void recreatesExceptionsUsingAvailableConstructors() throws Exception {
        ByteArray stringException = Util.exceptionToBuffer(new CustomStringException("boom"));
        ByteArray defaultException = Util.exceptionToBuffer(new CustomDefaultException());
        Throwable withStringConstructor = Util.exceptionFromBuffer(stringException.getArray(),
            stringException.getOffset(), stringException.getLength());
        Throwable withDefaultConstructor = Util.exceptionFromBuffer(defaultException.getArray(),
            defaultException.getOffset(), defaultException.getLength());

        assertThat(withStringConstructor).isInstanceOf(CustomStringException.class).hasMessage("boom");
        assertThat(withDefaultConstructor).isInstanceOf(CustomDefaultException.class);
    }

    @Test
    void discoversFieldsMethodsAndUpdatesFieldsThroughUtil() throws Exception {
        AnnotatedFixture fixture = new AnnotatedFixture();

        Field[] propertyFields = Util.getAllDeclaredFieldsWithAnnotations(AnnotatedFixture.class, Property.class);
        Method[] managedMethods = Util.getAllDeclaredMethodsWithAnnotations(AnnotatedFixture.class,
            ManagedAttribute.class);
        Method[] allMethods = Util.getAllMethods(AnnotatedFixture.class);

        assertThat(Arrays.stream(propertyFields).map(Field::getName)).contains("secret", "parentValue");
        assertThat(Arrays.stream(managedMethods).map(Method::getName)).contains("describedName");
        assertThat(Arrays.stream(allMethods).map(Method::getName)).contains("defaultDescription");

        Field secretField = Util.getField(AnnotatedFixture.class, "secret", true);
        Util.setField(secretField, fixture, "updated");
        assertThat(Util.getField(secretField, fixture)).isEqualTo("updated");
        assertThat(Util.findField(fixture, List.of("missing", "secret"))).isEqualTo(secretField);

        assertThat(Util.findMethod(fixture, List.of("missing", "setName"), String.class).getName())
            .isEqualTo("setName");
        assertThat(Util.findMethod(AnnotatedFixture.class, "describedName").getName()).isEqualTo("describedName");
        assertThat(Util.findMethod(AnnotatedFixture.class, "add", new Object[] {"1", "2"}).getName()).isEqualTo("add");
    }

    @Test
    void loadsClassesResourcesAndCustomAddressSuppliers() throws Exception {
        assertThat(Util.loadClass(SerializablePayload.class.getName(), Thread.currentThread().getContextClassLoader()))
            .isEqualTo(SerializablePayload.class);
        assertThat(Util.combine(new String[] {"a"}, new String[] {"b", "c"})).containsExactly("a", "b", "c");

        Set<Class<?>> assignableClasses = Util.findClassesAssignableFromPath("org_jgroups/jgroups", Marker.class,
            Thread.currentThread().getContextClassLoader());
        assertThat(assignableClasses).allMatch(Marker.class::isAssignableFrom);

        List<Class<?>> annotatedClasses = Util.findClassesAnnotatedWith("org_jgroups.jgroups", CoverageMarker.class);
        assertThat(annotatedClasses).allMatch(clazz -> clazz.isAnnotationPresent(CoverageMarker.class));

        try(InputStream stream = Util.getResourceAsStream(TEST_RESOURCE, UtilTest.class)) {
            assertThat(stream).isNotNull();
        }
        try(InputStream stream = Util.getResourceAsStream(TEST_RESOURCE, (ClassLoader)null)) {
            assertThat(stream).isNotNull();
        }

        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
            });
            try(InputStream stream = Util.getResourceAsStream(TEST_RESOURCE, (ClassLoader)null)) {
                if(stream != null) {
                    assertThat(stream.read()).isNotEqualTo(-1);
                }
            }
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }

        assertThat(Util.getAddressByCustomCode(LoopbackSupplier.class.getName()))
            .isEqualTo(InetAddress.getLoopbackAddress());
    }

    public interface Marker {
    }

    public interface Described {
        default String defaultDescription() {
            return "default";
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CoverageMarker {
    }

    public static class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int value;

        public SerializablePayload(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if(this == other) {
                return true;
            }
            if(!(other instanceof SerializablePayload)) {
                return false;
            }
            SerializablePayload that = (SerializablePayload)other;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    @CoverageMarker
    public static class ProbeStreamable implements Streamable, Marker {
        private String name;
        private int value;

        public ProbeStreamable() {
        }

        public ProbeStreamable(String name, int value) {
            this.name = name;
            this.value = value;
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
            if(this == other) {
                return true;
            }
            if(!(other instanceof ProbeStreamable)) {
                return false;
            }
            ProbeStreamable that = (ProbeStreamable)other;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    public static class CustomStringException extends Exception {
        private static final long serialVersionUID = 1L;

        public CustomStringException(String message) {
            super(message);
        }
    }

    public static class CustomDefaultException extends Exception {
        private static final long serialVersionUID = 1L;

        public CustomDefaultException() {
        }
    }

    public static class ParentFixture {
        @Property
        private String parentValue = "parent";
    }

    public static class AnnotatedFixture extends ParentFixture implements Described {
        @Property
        private String secret = "initial";

        @ManagedAttribute
        public String describedName() {
            return secret;
        }

        public void setName(String name) {
            secret = name;
        }

        public int add(int left, int right) {
            return left + right;
        }
    }

    public static class LoopbackSupplier implements Supplier<InetAddress> {
        @Override
        public InetAddress get() {
            return InetAddress.getLoopbackAddress();
        }
    }
}
