/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy_agent;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.Installer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ByteBuddyAgentTest {
    private static final String ALLOW_SELF_ATTACH = "jdk.attach.allowAttachSelf";
    private static final String JAVA_HOME = "java.home";

    private static String originalAllowSelfAttach;

    @BeforeAll
    static void requireExternalSelfAttachment() {
        originalAllowSelfAttach = System.getProperty(ALLOW_SELF_ATTACH);
        System.setProperty(ALLOW_SELF_ATTACH, Boolean.FALSE.toString());
    }

    @AfterAll
    static void restoreSelfAttachmentProperty() {
        restoreProperty(ALLOW_SELF_ATTACH, originalAllowSelfAttach);
    }

    @BeforeEach
    void resetInstaller() {
        Installer.premain(null, null);
    }

    @Test
    void getInstrumentationLooksUpInstallerViaSystemClassLoader() {
        assertThatThrownBy(ByteBuddyAgent::getInstrumentation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void getInstrumentationInvokesInstallerMethodSuccessfully() {
        Instrumentation instrumentation = new RecordingInstrumentation();

        Installer.premain(null, instrumentation);

        assertThat(ByteBuddyAgent.getInstrumentation()).isSameAs(instrumentation);
    }

    @Test
    void externalAttachmentCopiesAttacherClassResource(@TempDir Path temporaryDirectory) throws IOException {
        Path agentJar = Files.createFile(temporaryDirectory.resolve("agent.jar"));
        String originalLatentResolve = System.getProperty(ByteBuddyAgent.LATENT_RESOLVE);
        String originalJavaHome = System.getProperty(JAVA_HOME);
        System.setProperty(ByteBuddyAgent.LATENT_RESOLVE, Boolean.TRUE.toString());
        System.setProperty(JAVA_HOME, temporaryDirectory.resolve("missing-java-home").toString());
        try {
            assertThatThrownBy(() -> ByteBuddyAgent.attach(agentJar.toFile(),
                    ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE,
                    new AlwaysExternalAttachmentProvider()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Error during attachment");
        } finally {
            restoreProperty(ByteBuddyAgent.LATENT_RESOLVE, originalLatentResolve);
            restoreProperty(JAVA_HOME, originalJavaHome);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class AlwaysExternalAttachmentProvider implements ByteBuddyAgent.AttachmentProvider {
        @Override
        public ByteBuddyAgent.AttachmentProvider.Accessor attempt() {
            return new AlwaysExternalAccessor();
        }
    }

    private static final class AlwaysExternalAccessor implements ByteBuddyAgent.AttachmentProvider.Accessor {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isExternalAttachmentRequired() {
            return true;
        }

        @Override
        public Class<?> getVirtualMachineType() {
            return RecordingVirtualMachine.class;
        }

        @Override
        public ExternalAttachment getExternalAttachment() {
            return new ExternalAttachment(RecordingVirtualMachine.class.getName(), Collections.<File>emptyList());
        }
    }

    private static final class RecordingInstrumentation implements Instrumentation {
        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return false;
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
        }

        @Override
        public boolean isModifiableClass(Class<?> type) {
            return false;
        }

        @Override
        public Class<?>[] getAllLoadedClasses() {
            return new Class<?>[0];
        }

        @Override
        public Class<?>[] getInitiatedClasses(ClassLoader loader) {
            return new Class<?>[0];
        }

        @Override
        public long getObjectSize(Object object) {
            return 0L;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarFile) {
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarFile) {
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
        }

        @Override
        public void redefineModule(Module module,
                                   Set<Module> extraReads,
                                   Map<String, Set<Module>> extraExports,
                                   Map<String, Set<Module>> extraOpens,
                                   Set<Class<?>> extraUses,
                                   Map<Class<?>, List<Class<?>>> extraProvides) {
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }

    public static final class RecordingVirtualMachine {
        private static String attachedProcessId;
        private static String loadedAgent;
        private static String loadedAgentArgument;
        private static boolean detached;

        public static RecordingVirtualMachine attach(String processId) {
            attachedProcessId = processId;
            return new RecordingVirtualMachine();
        }

        public void loadAgent(String agent, String argument) {
            loadedAgent = agent;
            loadedAgentArgument = argument;
        }

        public void detach() {
            detached = true;
        }
    }
}
