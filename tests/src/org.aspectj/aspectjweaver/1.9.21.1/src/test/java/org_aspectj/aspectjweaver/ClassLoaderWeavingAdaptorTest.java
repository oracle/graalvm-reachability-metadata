/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor;
import org.aspectj.weaver.loadtime.IWeavingContext;
import org.aspectj.weaver.loadtime.definition.Definition;
import org.aspectj.weaver.tools.WeavingAdaptor;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderWeavingAdaptorTest {
    private static final String LINT_RESOURCE = "aspectj-ltw-lint.properties";

    private static boolean legacyDefineClassInvoked;

    @Test
    void initializesLoadTimeWeaverWithLintResourceAndGeneratedConcreteAspect() {
        ResourceTrackingClassLoader loader = new ResourceTrackingClassLoader(
                ClassLoaderWeavingAdaptorTest.class.getClassLoader(),
                LINT_RESOURCE,
                "adviceDidNotMatch=ignore\n"
        );
        Definition definition = concreteAspectDefinition();
        ClassLoaderWeavingAdaptor adaptor = new ClassLoaderWeavingAdaptor();

        boolean completed = false;
        try {
            adaptor.initialize(loader, new StaticWeavingContext(loader, definition));
            completed = true;
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        }

        assertThat(loader.resourceRequestCount()).isEqualTo(1);
        if (completed) {
            assertThat(adaptor.getMessageHolder()).isNotNull();
        }
    }

    @Test
    void initializesLegacyDefinitionPathInIsolatedAspectjLoader() throws Exception {
        Path resourceRoot = Files.createTempDirectory("aspectj-ltw-resources");
        Files.createDirectories(resourceRoot.resolve("META-INF"));
        Files.writeString(resourceRoot.resolve("META-INF/aop.xml"), legacyAopXml());
        Files.writeString(resourceRoot.resolve(LINT_RESOURCE), "adviceDidNotMatch=ignore\n");

        String originalJavaVersion = System.getProperty("java.version");
        URL aspectjLocation = ClassLoaderWeavingAdaptor.class.getProtectionDomain().getCodeSource().getLocation();
        URL[] urls = {resourceRoot.toUri().toURL(), aspectjLocation};
        boolean completed = false;
        boolean expectsLegacyDefineClass = false;
        try (URLClassLoader isolatedLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            System.setProperty("java.version", "1.8.0");
            legacyDefineClassInvoked = false;
            expectsLegacyDefineClass = usesLegacyDefineClassPath(isolatedLoader);
            initializeAdaptorFrom(isolatedLoader);
            completed = true;
        } catch (Throwable throwable) {
            rethrowIfNotNativeImageDynamicClassLoadingError(throwable);
        } finally {
            if (originalJavaVersion == null) {
                System.clearProperty("java.version");
            } else {
                System.setProperty("java.version", originalJavaVersion);
            }
        }

        if (completed) {
            assertThat(resourceRoot.resolve("META-INF/aop.xml")).exists();
            if (expectsLegacyDefineClass) {
                assertThat(legacyDefineClassInvoked).isTrue();
            }
        }
    }

    private static void initializeAdaptorFrom(URLClassLoader isolatedLoader) throws Exception {
        Class<?> adaptorClass = Class.forName(
                "org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor",
                true,
                isolatedLoader
        );
        Object adaptor = adaptorClass.getConstructor().newInstance();
        installLegacyDefineClassBridge(adaptorClass);
        Class<?> contextClass = Class.forName("org.aspectj.weaver.loadtime.IWeavingContext", false, isolatedLoader);
        Method initialize = adaptorClass.getMethod("initialize", ClassLoader.class, contextClass);
        try {
            initialize.invoke(adaptor, isolatedLoader, null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }

    private static void installLegacyDefineClassBridge(Class<?> adaptorClass) throws NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException {
        Field defineClassMethod = adaptorClass.getDeclaredField("defineClassMethod");
        defineClassMethod.setAccessible(true);
        defineClassMethod.set(null, LegacyDefineClassBridge.class.getMethod(
                "defineClass",
                String.class,
                byte[].class,
                Integer.TYPE,
                Integer.TYPE,
                ClassLoader.class,
                ProtectionDomain.class
        ));
    }

    private static boolean usesLegacyDefineClassPath(URLClassLoader isolatedLoader) throws Exception {
        Class<?> langUtilClass = Class.forName("org.aspectj.util.LangUtil", true, isolatedLoader);
        Method is11VmOrGreater = langUtilClass.getMethod("is11VMOrGreater");
        return !((Boolean) is11VmOrGreater.invoke(null));
    }

    private static String legacyAopXml() {
        return """
                <aspectj>
                    <weaver options="-Xlintfile:%s"/>
                    <aspects>
                        <concrete-aspect
                            name="org_aspectj.aspectjweaver.generated.LegacyConcreteAspect"
                            precedence="org_aspectj.aspectjweaver..*"/>
                    </aspects>
                </aspectj>
                """.formatted(LINT_RESOURCE);
    }

    private static Definition concreteAspectDefinition() {
        Definition definition = new Definition();
        definition.appendWeaverOptions("-Xlintfile:" + LINT_RESOURCE);
        definition.getConcreteAspects().add(new Definition.ConcreteAspect(
                "org_aspectj.aspectjweaver.generated.LoadTimeConcreteAspect",
                null,
                "org_aspectj.aspectjweaver..*",
                null
        ));
        return definition;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Throwable throwable) {
        if (!hasUnsupportedFeatureError(throwable) && !hasUnsupportedIsolatedClassLoadingFailure(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new AssertionError(throwable);
        }
    }

    private static boolean hasUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasUnsupportedIsolatedClassLoadingFailure(Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException
                    && "org.aspectj.util.LangUtil".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class StaticWeavingContext implements IWeavingContext {
        private final ClassLoader classLoader;
        private final List<Definition> definitions;

        private StaticWeavingContext(ClassLoader classLoader, Definition definition) {
            this.classLoader = classLoader;
            this.definitions = Collections.singletonList(definition);
        }

        @Override
        public Enumeration<URL> getResources(String name) {
            return Collections.emptyEnumeration();
        }

        @Override
        public String getBundleIdFromURL(URL url) {
            return null;
        }

        @Override
        public String getClassLoaderName() {
            return "class-loader-weaving-adaptor-test";
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public String getFile(URL url) {
            return url.toExternalForm();
        }

        @Override
        public String getId() {
            return "class-loader-weaving-adaptor-test";
        }

        @Override
        public boolean isLocallyDefined(String classname) {
            return classname.startsWith("org_aspectj.aspectjweaver");
        }

        @Override
        public List<Definition> getDefinitions(ClassLoader loader, WeavingAdaptor adaptor) {
            return definitions;
        }
    }

    public static final class LegacyDefineClassBridge {
        private LegacyDefineClassBridge() {
        }

        public static Class<?> defineClass(String name, byte[] bytes, int offset, int length, ClassLoader loader,
                ProtectionDomain protectionDomain) {
            legacyDefineClassInvoked = true;
            return Object.class;
        }
    }

    private static final class ResourceTrackingClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] contents;
        private int resourceRequestCount;

        private ResourceTrackingClassLoader(ClassLoader parent, String resourceName, String contents) {
            super(parent);
            this.resourceName = resourceName;
            this.contents = contents.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                resourceRequestCount++;
                return new ByteArrayInputStream(contents);
            }
            return super.getResourceAsStream(name);
        }

        private int resourceRequestCount() {
            return resourceRequestCount;
        }
    }
}
