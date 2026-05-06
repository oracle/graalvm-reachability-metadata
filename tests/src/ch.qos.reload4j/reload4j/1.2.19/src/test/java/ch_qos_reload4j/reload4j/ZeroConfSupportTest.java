/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.apache.log4j.net.ZeroConfSupport;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

public class ZeroConfSupportTest {
    private static final String ZERO_CONF_SUPPORT_CLASS_NAME = "org.apache.log4j.net.ZeroConfSupport";
    private static final String ZERO_CONF_SUPPORT_RESOURCE = "org/apache/log4j/net/ZeroConfSupport.class";

    @Test
    void constructsAdvertisesAndUnadvertisesUsingJmDns3Api() {
        Map properties = new HashMap();
        properties.put("application", "reload4j-test");

        ZeroConfSupport zeroConfSupport = new ZeroConfSupport("_log4j._tcp.local.", 4560, "reload4j", properties);
        Object jmDNSInstance = ZeroConfSupport.getJMDNSInstance();

        assertThat(jmDNSInstance).isInstanceOf(JmDNS.class);
        JmDNS jmDNS = (JmDNS) jmDNSInstance;

        zeroConfSupport.advertise();

        ServiceInfo registeredService = jmDNS.getRegisteredService();
        assertThat(registeredService).isNotNull();
        assertThat(registeredService.getType()).isEqualTo("_log4j._tcp.local.");
        assertThat(registeredService.getName()).isEqualTo("reload4j");
        assertThat(registeredService.getPort()).isEqualTo(4560);
        assertThat(registeredService.getProperties()).containsEntry("application", "reload4j-test");

        zeroConfSupport.unadvertise();

        assertThat(jmDNS.getRegisteredService()).isNull();
    }

    @Test
    void constructsAdvertisesAndUnadvertisesUsingLegacyJmDns1Api() throws Exception {
        try {
            LegacyJmDnsClassLoader classLoader = new LegacyJmDnsClassLoader();
            Class<?> zeroConfSupportClass = Class.forName(ZERO_CONF_SUPPORT_CLASS_NAME, true, classLoader);
            Map properties = new HashMap();
            properties.put("application", "legacy-reload4j-test");

            Object zeroConfSupport = zeroConfSupportClass
                    .getConstructor(String.class, int.class, String.class, Map.class)
                    .newInstance("_log4j._tcp.local.", 4561, "legacy-reload4j", properties);
            Object jmDNSInstance = zeroConfSupportClass.getMethod("getJMDNSInstance").invoke(null);

            assertThat(jmDNSInstance).isNotNull();

            zeroConfSupportClass.getMethod("advertise").invoke(zeroConfSupport);
            zeroConfSupportClass.getMethod("unadvertise").invoke(zeroConfSupport);
        } catch (InvocationTargetException exception) {
            if (isUnsupportedNativeImageError(exception.getCause())) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!isUnsupportedNativeImageError(error)) {
                throw error;
            }
        }
    }

    private static boolean isUnsupportedNativeImageError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return true;
        }
        Throwable cause = throwable.getCause();
        return cause != null && cause != throwable && isUnsupportedNativeImageError(cause);
    }

    private static byte[] readZeroConfSupportBytes() throws IOException {
        ClassLoader classLoader = ZeroConfSupportTest.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(ZERO_CONF_SUPPORT_RESOURCE)) {
            assertThat(inputStream).isNotNull();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static byte[] createLegacyJmDNSBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "javax/jmdns/JmDNS", null, "java/lang/Object", null);
        addDefaultConstructor(classWriter);
        addEmptyServiceMethod(classWriter, "registerService");
        addEmptyServiceMethod(classWriter, "unregisterService");
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static byte[] createLegacyServiceInfoBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, "javax/jmdns/ServiceInfo", null, "java/lang/Object", null);
        MethodVisitor constructor = classWriter.visitMethod(
                ACC_PUBLIC,
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;IIILjava/util/Hashtable;)V",
                null,
                null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        classWriter.visitEnd();
        return classWriter.toByteArray();
    }

    private static void addDefaultConstructor(ClassWriter classWriter) {
        MethodVisitor constructor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void addEmptyServiceMethod(ClassWriter classWriter, String methodName) {
        MethodVisitor method = classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                "(Ljavax/jmdns/ServiceInfo;)V",
                null,
                null);
        method.visitCode();
        method.visitInsn(RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static final class LegacyJmDnsClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes = new HashMap<>();

        private LegacyJmDnsClassLoader() throws IOException {
            super(ZeroConfSupportTest.class.getClassLoader());
            classes.put(ZERO_CONF_SUPPORT_CLASS_NAME, readZeroConfSupportBytes());
            classes.put("javax.jmdns.JmDNS", createLegacyJmDNSBytes());
            classes.put("javax.jmdns.ServiceInfo", createLegacyServiceInfoBytes());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }
}
