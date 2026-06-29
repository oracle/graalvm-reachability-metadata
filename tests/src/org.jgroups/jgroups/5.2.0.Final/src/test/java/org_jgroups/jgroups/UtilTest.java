/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.StateTransferException;
import org.jgroups.SuspectedException;
import org.jgroups.blocks.ReplCache;
import org.jgroups.protocols.STATS;
import org.jgroups.util.Average;
import org.jgroups.util.ByteArray;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilTest {
    @BeforeAll
    static void configureLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    @Test
    void serializesObjectsThroughByteBuffersAndStreams() throws Exception {
        ReplCache.Value<String> value = new ReplCache.Value<>("payload", (short) 2);

        byte[] buffer = Util.objectToByteBuffer(value);
        ReplCache.Value<String> fromByteArray = Util.objectFromByteBuffer(buffer);
        ReplCache.Value<String> fromByteBuffer = Util.objectFromByteBuffer(
                ByteBuffer.wrap(buffer), Util.class.getClassLoader());

        ByteArrayDataOutputStream output = new ByteArrayDataOutputStream(256, true);
        Util.objectToStream(value, output);
        ReplCache.Value<String> fromStream = Util.objectFromStream(
                new ByteArrayDataInputStream(output.buffer(), 0, output.position()));

        assertThat(fromByteArray.getVal()).isEqualTo("payload");
        assertThat(fromByteBuffer.getReplicationCount()).isEqualTo((short) 2);
        assertThat(fromStream.toString()).isEqualTo("payload (2)");
    }

    @Test
    void reconstructsStreamablesWithConstructors() throws Exception {
        Average average = new Average().add(10).add(20);

        byte[] streamableBytes = Util.streamableToByteBuffer(average);
        Average fromByteArray = Util.streamableFromByteBuffer(Average.class, streamableBytes);
        Average fromByteBuffer = Util.streamableFromByteBuffer(Average.class, ByteBuffer.wrap(streamableBytes));

        ByteArray genericBuffer = Util.objectToBuffer(average);
        Average generic = Util.objectFromByteBuffer(
                genericBuffer.getArray(),
                genericBuffer.getOffset(),
                genericBuffer.getLength(),
                Util.class.getClassLoader());

        Average[] averages = {new Average().add(1), new Average().add(3)};
        ByteArrayDataOutputStream output = new ByteArrayDataOutputStream(128, true);
        Util.write(averages, output);
        Average[] restored = Util.read(
                Average.class,
                new ByteArrayDataInputStream(output.buffer(), 0, output.position()));

        assertThat(fromByteArray.average()).isEqualTo(15.0);
        assertThat(fromByteBuffer.count()).isEqualTo(2);
        assertThat(generic.average()).isEqualTo(15.0);
        assertThat(restored).extracting(Average::average).containsExactly(1.0, 3.0);
    }

    @Test
    void recreatesExceptionsUsingAvailableConstructors() throws Exception {
        StateTransferException withMessage = new StateTransferException("state copy failed");
        SuspectedException withoutMessageConstructor = new SuspectedException("node-a");

        ByteArray withMessageBuffer = Util.exceptionToBuffer(withMessage);
        ByteArray withoutMessageConstructorBuffer = Util.exceptionToBuffer(withoutMessageConstructor);
        Throwable restoredWithMessage = Util.exceptionFromBuffer(
                withMessageBuffer.getArray(), 0, withMessageBuffer.getLength());
        Throwable restoredWithoutMessageConstructor = Util.exceptionFromBuffer(
                withoutMessageConstructorBuffer.getArray(),
                0,
                withoutMessageConstructorBuffer.getLength());

        assertThat(restoredWithMessage)
                .isInstanceOf(StateTransferException.class)
                .hasMessage("state copy failed");
        assertThat(restoredWithoutMessageConstructor).isInstanceOf(SuspectedException.class);
    }

    @Test
    void locatesAndUsesFieldsAndMethodsViaUtil() throws Exception {
        Average average = new Average().add(7);

        Field averageField = Util.getField(Average.class, "avg", true);
        Util.setField(averageField, average, 42.0);
        Field foundField = Util.findField(average, Arrays.asList("missing", "count"));
        Util.setField(foundField, average, 4L);

        Method countByList = Util.findMethod(Average.class, List.of("missing", "count"));
        Method averageByName = Util.findMethod(Average.class, "average");
        Method methodByPrimitiveArguments = Util.findMethod(Average.class, "add", new Object[] {"5"});
        Method[] allMethods = Util.getAllMethods(Average.class);
        Field[] allFields = Util.getAllDeclaredFieldsWithAnnotations(Average.class);
        Method[] declaredMethods = Util.getAllDeclaredMethodsWithAnnotations(Average.class);

        assertThat(Util.getField(averageField, average)).isEqualTo(42.0);
        assertThat(Util.getField(foundField, average)).isEqualTo(4L);
        assertThat(countByList).isNotNull();
        assertThat(averageByName).isNotNull();
        assertThat(methodByPrimitiveArguments).isNotNull();
        assertThat(allMethods).extracting(Method::getName).contains("add", "average", "count");
        assertThat(allFields).extracting(Field::getName).contains("avg", "count");
        assertThat(declaredMethods).extracting(Method::getName).contains("writeTo", "readFrom");
    }

    @Test
    void inspectsAnnotatedFieldsAndCombinesTypedArrays() {
        STATS stats = new STATS();
        List<Object> components = Util.getComponents(stats);
        List<String> componentNames = new ArrayList<>();
        List<String> componentTypes = new ArrayList<>();

        Util.forAllComponents(stats, (component, name) -> componentNames.add(name));
        Util.forAllComponentTypes(STATS.class, (type, name) -> componentTypes.add(type.getName() + ":" + name));
        String[] combined = Util.combine(new String[] {"a"}, new String[] {"b", "c"});

        assertThat(components).hasSize(1);
        assertThat(componentNames).containsExactly("mstats");
        assertThat(componentTypes).contains("org.jgroups.protocols.MsgStats:mstats");
        assertThat(combined).containsExactly("a", "b", "c");
    }

    @Test
    void loadsClassesAndResourcesThroughUtilityFallbacks() throws Exception {
        Class<?> loadedClass = Util.loadClass("org.jgroups.util.Average", Util.class.getClassLoader());

        assertThat(Util.findClassesAssignableFromPath(
                "missing/jgroups/package", Average.class, Util.class.getClassLoader())).isEmpty();
        assertThat(Util.findClassesAnnotatedWith("missing.jgroups.package", Deprecated.class)).isEmpty();

        Set<Class<?>> assignableClasses = Util.findClassesAssignableFromPath(
                "org_jgroups/jgroups", Object.class, UtilTest.class.getClassLoader());
        List<Class<?>> annotatedClasses = Util.findClassesAnnotatedWith("org_jgroups.jgroups", Deprecated.class);
        assertThat(assignableClasses).doesNotContain(String.class);
        assertThat(annotatedClasses).doesNotContain(String.class);

        try (InputStream fromExplicitLoader = Util.getResourceAsStream(
                "jg-messages.properties", Util.class.getClassLoader());
             InputStream fromContextLoader = Util.getResourceAsStream("jg-messages.properties", (ClassLoader) null)) {
            assertThat(loadedClass).isEqualTo(Average.class);
            assertThat(fromExplicitLoader).isNotNull();
            assertThat(fromContextLoader).isNotNull();
        }

        ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            InputStream missingFromSystemLoader = Util.getResourceAsStream(
                    "missing-jgroups-resource.properties", (ClassLoader) null);
            assertThat(missingFromSystemLoader).isNull();
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalContextLoader);
        }
    }

    @Test
    void createsAddressFromCustomSupplierCode() throws Exception {
        InetAddress address = Util.getAddressByCustomCode(LoopbackAddressSupplier.class.getName());

        assertThat(address.isLoopbackAddress()).isTrue();
    }

    @Deprecated
    public static class LoopbackAddressSupplier implements Supplier<InetAddress> {
        @Override
        public InetAddress get() {
            return InetAddress.getLoopbackAddress();
        }
    }
}
