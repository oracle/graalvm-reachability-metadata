/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.asm.ClassReader;
import org.modelmapper.internal.asm.ClassWriter;
import org.modelmapper.internal.asm.Type;
import org.modelmapper.internal.asm.commons.ClassRemapper;
import org.modelmapper.internal.asm.commons.Remapper;
import org.modelmapper.internal.bytebuddy.ClassFileVersion;

public class ClassFileVersionInnerVersionLocatorInnerResolverTest {
    private static final int JAVA_VERSION = 8;
    private static final String RUNTIME_TYPE = "java/lang/Runtime";
    private static final String PROXY_RUNTIME_TYPE = Type.getInternalName(ProxyRuntime.class);

    @Test
    void resolvesCurrentRuntimeMajorVersionWhenFeatureMethodIsUnavailable() throws Exception {
        ClassLoader classLoader = new RuntimeVersionRedirectingClassLoader(getClass().getClassLoader());
        Class<?> runnerType = classLoader.loadClass(IsolatedRunner.class.getName());
        Callable<?> runner = (Callable<?>) runnerType.getConstructor().newInstance();

        assertThat(runner.call()).isEqualTo(Boolean.TRUE);
    }

    public static final class IsolatedRunner implements Callable<Boolean> {
        @Override
        public Boolean call() {
            return ClassFileVersion.ofThisVm().getJavaVersion() == JAVA_VERSION;
        }
    }

    public static final class ProxyRuntime {
        private static final Version VERSION = new Version(JAVA_VERSION);

        private ProxyRuntime() {
        }

        public static Version version() {
            return VERSION;
        }

        public static final class Version {
            private final int major;

            public Version(int major) {
                this.major = major;
            }

            public int major() {
                return major;
            }
        }
    }

    private static final class RuntimeVersionRedirectingClassLoader extends ClassLoader {
        private RuntimeVersionRedirectingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null && isChildFirst(name)) {
                    try {
                        type = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        type = super.loadClass(name, false);
                    }
                } else if (type == null) {
                    type = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] binaryRepresentation = inputStream.readAllBytes();
                if (isClassFileVersionType(name)) {
                    binaryRepresentation = redirectRuntimeReferences(binaryRepresentation);
                }
                return defineClass(name, binaryRepresentation, 0, binaryRepresentation.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        private static boolean isChildFirst(String name) {
            return isClassFileVersionType(name)
                || IsolatedRunner.class.getName().equals(name)
                || name.startsWith(ProxyRuntime.class.getName());
        }

        private static boolean isClassFileVersionType(String name) {
            return name.equals(ClassFileVersion.class.getName())
                || name.startsWith(ClassFileVersion.class.getName() + "$");
        }

        private static byte[] redirectRuntimeReferences(byte[] binaryRepresentation) {
            ClassReader reader = new ClassReader(binaryRepresentation);
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassRemapper(writer, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (RUNTIME_TYPE.equals(internalName)) {
                        return PROXY_RUNTIME_TYPE;
                    }
                    return internalName;
                }
            }), 0);
            return writer.toByteArray();
        }
    }
}
