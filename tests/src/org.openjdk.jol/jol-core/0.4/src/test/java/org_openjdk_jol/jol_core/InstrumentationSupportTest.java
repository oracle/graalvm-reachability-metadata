/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.spi.AttachProvider;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.util.VMSupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentationSupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void printableLayoutRequestsInstrumentationSizeThroughPublicApi() throws Exception {
        String originalJavaHome = System.getProperty("java.home");

        try {
            configureLegacyToolsJarLookup(originalJavaHome);

            Sample sample = new Sample(42, "jol");
            ClassLayout layout = ClassLayout.parseInstance(sample);

            String printable = layout.toPrintable();
            int size = VMSupport.sizeOf(sample);

            assertThat(printable)
                    .contains(Sample.class.getCanonicalName() + " object internals:")
                    .contains("Instance size:");
            assertThat(size).isPositive();
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            System.setProperty("java.home", originalJavaHome);
        }
    }

    private void configureLegacyToolsJarLookup(String originalJavaHome) throws Exception {
        Path attachModule = Path.of(originalJavaHome, "jmods", "jdk.attach.jmod");
        if (!Files.isRegularFile(attachModule)) {
            return;
        }

        Path fakeJdkHome = temporaryDirectory.resolve("fake-jdk");
        Path fakeJreHome = fakeJdkHome.resolve("jre");
        Path toolsJar = fakeJdkHome.resolve("lib").resolve("tools.jar");
        Files.createDirectories(toolsJar.getParent());
        createToolsJarFromAttachModule(attachModule, toolsJar);
        System.setProperty("java.home", fakeJreHome.toString());
    }

    private static void createToolsJarFromAttachModule(Path attachModule, Path toolsJar) throws Exception {
        try (InputStream inputStream = Files.newInputStream(attachModule);
                JarInputStream input = new JarInputStream(inputStream);
                OutputStream outputStream = Files.newOutputStream(toolsJar);
                JarOutputStream output = new JarOutputStream(outputStream)) {
            JarEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!isAttachClass(name)) {
                    continue;
                }
                output.putNextEntry(new JarEntry(name.substring("classes/".length())));
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.closeEntry();
            }
            copyTestClass(output, InstrumentationSupportTest.class);
            copyTestClass(output, TestAttachProvider.class);
            copyTestClass(output, TestVirtualMachine.class);
            output.putNextEntry(new JarEntry("META-INF/services/com.sun.tools.attach.spi.AttachProvider"));
            output.write(TestAttachProvider.class.getName().getBytes(StandardCharsets.UTF_8));
            output.write('\n');
            output.closeEntry();
        }
    }

    private static boolean isAttachClass(String name) {
        return name.endsWith(".class") && name.startsWith("classes/com/sun/tools/attach/");
    }

    private static void copyTestClass(JarOutputStream output, Class<?> type) throws Exception {
        String resourceName = type.getName().replace('.', '/') + ".class";
        output.putNextEntry(new JarEntry(resourceName));
        try (InputStream input = InstrumentationSupportTest.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertThat(input).isNotNull();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        output.closeEntry();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    static class Sample {
        private final int number;
        private final String text;

        Sample(int number, String text) {
            this.number = number;
            this.text = text;
        }
    }

    public static final class TestAttachProvider extends AttachProvider {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public VirtualMachine attachVirtualMachine(String id) throws AttachNotSupportedException {
            return new TestVirtualMachine(this, id);
        }

        @Override
        public List<VirtualMachineDescriptor> listVirtualMachines() {
            return Collections.emptyList();
        }
    }

    public static final class TestVirtualMachine extends VirtualMachine {
        TestVirtualMachine(AttachProvider provider, String id) {
            super(provider, id);
        }

        @Override
        public void detach() throws IOException {
        }

        @Override
        public void loadAgentLibrary(String agentLibrary, String options)
                throws AgentLoadException, AgentInitializationException, IOException {
        }

        @Override
        public void loadAgentPath(String agentLibrary, String options)
                throws AgentLoadException, AgentInitializationException, IOException {
        }

        @Override
        public void loadAgent(String agent, String options)
                throws AgentLoadException, AgentInitializationException, IOException {
        }

        @Override
        public Properties getSystemProperties() throws IOException {
            return new Properties();
        }

        @Override
        public Properties getAgentProperties() throws IOException {
            return new Properties();
        }

        @Override
        public void startManagementAgent(Properties agentProperties) throws IOException {
        }

        @Override
        public String startLocalManagementAgent() throws IOException {
            return "";
        }
    }
}
