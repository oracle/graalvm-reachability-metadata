/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.AbstractMemberAccess;
import ognl.MethodFailedException;
import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import ognl.security.OgnlSecurityManager;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Permission;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OgnlRuntimeTest {
    @Test
    void discoversConstructorsAndCreatesObjects() throws Exception {
        final OgnlContext context = newContext();

        final Object value = OgnlRuntime.callConstructor(
                context,
                ConstructedValue.class.getName(),
                new Object[] {"created", 7});

        assertThat(value).isInstanceOf(ConstructedValue.class);
        assertThat(((ConstructedValue) value).describe()).isEqualTo("created:7");
    }

    @Test
    void readsWritesAndDiscoversFields() throws Exception {
        final OgnlContext context = newContext();
        final FieldFixture fixture = new FieldFixture();

        assertThat(OgnlRuntime.getFields(FieldFixture.class)).containsKeys("name", "KIND");
        assertThat(OgnlRuntime.getFieldValue(context, fixture, "name")).isEqualTo("initial");
        assertThat(OgnlRuntime.setFieldValue(context, fixture, "name", "updated")).isTrue();
        assertThat(fixture.name).isEqualTo("updated");
        assertThat(OgnlRuntime.getStaticField(context, FieldFixture.class.getName(), "KIND"))
                .isEqualTo("field-fixture");
    }

    @Test
    void discoversInstanceStaticAndAllMethods() {
        final Map<?, ?> instanceMethods = OgnlRuntime.getMethods(MethodDiscoveryFixture.class, false);
        final Map<?, ?> staticMethods = OgnlRuntime.getMethods(StaticMethodDiscoveryFixture.class, true);
        final Map<?, ?> allMethods = OgnlRuntime.getAllMethods(AllMethodDiscoveryFixture.class, false);

        assertThat(instanceMethods.containsKey("instanceMessage")).isTrue();
        assertThat(staticMethods.containsKey("staticMessage")).isTrue();
        assertThat(allMethods.containsKey("allMessage")).isTrue();
    }

    @Test
    void discoversBeanAccessors() {
        assertThat(OgnlRuntime.getDeclaredMethods(AccessorFixture.class, "name", false)).isNotEmpty();
        assertThat(OgnlRuntime.getDeclaredMethods(AccessorFixture.class, "name", true)).isNotEmpty();
        assertThat(OgnlRuntime.getReadMethod(ReadMethodFixture.class, "label")).isNotNull();
        assertThat(OgnlRuntime.getWriteMethod(WriteMethodFixture.class, "missingProperty")).isNull();
    }

    @Test
    void invokesRegularGenericAndVarargsMethods() throws Exception {
        final OgnlContext context = newContext();
        final InvocationFixture invocationFixture = new InvocationFixture("Ada");
        final StringArrayBox stringArrayBox = new StringArrayBox();
        final VarargsFixture singleVararg = new VarargsFixture();
        final VarargsFixture multipleVarargs = new VarargsFixture();

        assertThat(OgnlRuntime.callMethod(context, invocationFixture, "greet", new Object[] {"Hello"}))
                .isEqualTo("Hello Ada");
        assertThat(OgnlRuntime.callMethod(context, stringArrayBox, "firstItem", new Object[] {new String[] {"first"}}))
                .isEqualTo("first");
        assertThat(OgnlRuntime.callMethod(context, singleVararg, "join", new Object[] {"solo"}))
                .isEqualTo("solo");
        assertThat(OgnlRuntime.callMethod(context, multipleVarargs, "join", new Object[] {"left", "right"}))
                .isEqualTo("left,right");
    }

    @Test
    void fallsBackToPublicMembersWhenDeclaredMemberAccessIsDenied() {
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final PublicMemberDenyingSecurityManager securityManager = new PublicMemberDenyingSecurityManager();
        final Class<?> fieldFallbackClass = PublicFieldFallbackFixture.class;
        final Class<?> methodFallbackClass = PublicMethodFallbackFixture.class;
        final Class<?> accessorFallbackClass = PublicAccessorFallbackFixture.class;
        final Runnable fieldsLookup = () -> OgnlRuntime.getFields(fieldFallbackClass);
        final Runnable methodsLookup = () -> OgnlRuntime.getMethods(methodFallbackClass, false);
        final Runnable accessorsLookup = () -> OgnlRuntime.getDeclaredMethods(accessorFallbackClass, "name", false);
        final boolean installed = installSecurityManager(securityManager);
        try {
            if (installed) {
                assertSecurityExceptionFrom(fieldsLookup);
                assertSecurityExceptionFrom(methodsLookup);
                assertSecurityExceptionFrom(accessorsLookup);
            } else {
                assertThat(System.getSecurityManager()).isSameAs(previousSecurityManager);
            }
        } finally {
            restoreSecurityManager(previousSecurityManager);
        }
    }

    @Test
    void invokesMethodWhenOgnlSecurityManagerWasForceDisabledDuringRuntimeInitialization() throws Exception {
        try {
            runIsolatedScenario(ForceDisabledScenario.class.getName());
        } catch (ClassNotFoundException exception) {
            if (!isUnsupportedNativeImageClasspathReload(exception, ForceDisabledScenario.class.getName())) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void invokesMethodThroughPreinstalledOgnlSandbox() throws Exception {
        final OgnlContext context = newContext();
        final InvocationFixture fixture = new InvocationFixture("Grace");
        final String previousValue = System.getProperty("ognl.security.manager");
        final SecurityManager previousSecurityManager = System.getSecurityManager();
        final SetSecurityManagerDenyingSecurityManager parentSecurityManager =
                new SetSecurityManagerDenyingSecurityManager();
        final OgnlSecurityManager ognlSecurityManager = new OgnlSecurityManager(parentSecurityManager);
        final boolean installed = installSecurityManager(ognlSecurityManager);
        System.setProperty("ognl.security.manager", "true");
        try {
            if (installed) {
                assertThat(OgnlRuntime.callMethod(context, fixture, "greet", new Object[] {"Hi"}))
                        .isEqualTo("Hi Grace");

                parentSecurityManager.denySetSecurityManager = true;
                assertThat(OgnlRuntime.callMethod(context, fixture, "greet", new Object[] {"Welcome"}))
                        .isEqualTo("Welcome Grace");
                parentSecurityManager.denySetSecurityManager = false;
            } else {
                try {
                    assertThat(OgnlRuntime.callMethod(context, fixture, "greet", new Object[] {"Hi"}))
                            .isEqualTo("Hi Grace");
                } catch (MethodFailedException exception) {
                    assertThat(securityManagerInstallationIsDisabled(exception)).isTrue();
                } catch (Error error) {
                    if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                        throw error;
                    }
                }
            }
        } finally {
            parentSecurityManager.denySetSecurityManager = false;
            restoreSecurityManager(previousSecurityManager);
            restoreProperty("ognl.security.manager", previousValue);
        }
    }

    private static OgnlContext newContext() {
        return new OgnlContext(null, null, new AllowAllMemberAccess());
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static boolean installSecurityManager(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static void restoreSecurityManager(SecurityManager previousSecurityManager) {
        try {
            System.setSecurityManager(previousSecurityManager);
        } catch (SecurityException | UnsupportedOperationException exception) {
            assertThat(System.getSecurityManager()).isSameAs(previousSecurityManager);
        }
    }

    private static void assertSecurityExceptionFrom(Runnable invocation) {
        boolean thrown = false;
        try {
            invocation.run();
        } catch (SecurityException exception) {
            thrown = true;
        }
        assertThat(thrown).isTrue();
    }

    private static void runIsolatedScenario(String scenarioClassName) throws Exception {
        final URL[] classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(File::new)
                .map(OgnlRuntimeTest::toUrl)
                .toArray(URL[]::new);

        try (URLClassLoader classLoader = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader())) {
            final Class<?> scenarioClass = Class.forName(scenarioClassName, true, classLoader);
            scenarioClass.getMethod("run").invoke(null);
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        }
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid classpath entry: " + file, exception);
        }
    }

    private static boolean securityManagerInstallationIsDisabled(MethodFailedException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnsupportedOperationException) {
                return true;
            }
            if (current instanceof OgnlException) {
                final Throwable reason = ((OgnlException) current).getReason();
                if (reason != null && reason != current.getCause()) {
                    current = reason;
                    continue;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isUnsupportedNativeImageClasspathReload(
            Throwable throwable, String expectedClassName) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException
                    && expectedClassName.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class AllowAllMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return true;
        }
    }

    private static class PublicMemberDenyingSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPackageAccess(String packageName) {
            if (OgnlRuntimeTest.class.getPackage().getName().equals(packageName)) {
                throw new SecurityException("Package access denied for fallback coverage");
            }
        }
    }

    private static final class SetSecurityManagerDenyingSecurityManager extends SecurityManager {
        private boolean denySetSecurityManager;

        @Override
        public void checkPermission(Permission permission) {
            if (denySetSecurityManager && "setSecurityManager".equals(permission.getName())) {
                throw new SecurityException("Security manager replacement denied for fallback coverage");
            }
        }
    }

    public static final class ConstructedValue {
        private final String name;
        private final int count;

        public ConstructedValue(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String describe() {
            return name + ":" + count;
        }
    }

    public static final class FieldFixture {
        public static String KIND = "field-fixture";
        public String name = "initial";
    }

    public static final class PublicFieldFallbackFixture {
        public static String VISIBLE = "visible";
    }

    public static final class MethodDiscoveryFixture {
        public String instanceMessage() {
            return "instance";
        }
    }

    public static final class PublicMethodFallbackFixture {
        public String visibleMethod() {
            return "visible";
        }
    }

    public static final class StaticMethodDiscoveryFixture {
        public static String staticMessage() {
            return "static";
        }
    }

    public static final class AllMethodDiscoveryFixture {
        public String allMessage() {
            return "all";
        }
    }

    public static final class AccessorFixture {
        private String name = "accessor";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static final class PublicAccessorFallbackFixture {
        public String getName() {
            return "fallback";
        }
    }

    public static final class ReadMethodFixture {
        public String getLabel() {
            return "read";
        }
    }

    public static final class WriteMethodFixture {
        public String unrelated() {
            return "write";
        }
    }

    public static final class InvocationFixture {
        private final String name;

        public InvocationFixture(String name) {
            this.name = name;
        }

        public String greet(String salutation) {
            return salutation + " " + name;
        }
    }

    public abstract static class GenericArrayBox<T> {
        public T firstItem(T[] items) {
            return items[0];
        }
    }

    public static final class StringArrayBox extends GenericArrayBox<String> {
    }

    public static final class VarargsFixture {
        public String join(String... values) {
            return String.join(",", values);
        }
    }

    public static final class ForceDisabledScenario {
        public static void run() throws Exception {
            final String previousValue = System.getProperty("ognl.security.manager");
            System.setProperty("ognl.security.manager", "forceDisableOnInit");
            try {
                final OgnlContext context = new OgnlContext(null, null, new ForceDisabledMemberAccess());
                final ForceDisabledInvocationFixture fixture = new ForceDisabledInvocationFixture("Linus");
                final Object result = OgnlRuntime.callMethod(context, fixture, "greet", new Object[] {"Hello"});
                if (!"Hello Linus".equals(result)) {
                    throw new AssertionError("Unexpected OGNL method invocation result: " + result);
                }
            } finally {
                if (previousValue == null) {
                    System.clearProperty("ognl.security.manager");
                } else {
                    System.setProperty("ognl.security.manager", previousValue);
                }
            }
        }

        private static final class ForceDisabledMemberAccess extends AbstractMemberAccess {
            @Override
            public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
                return true;
            }
        }

        public static final class ForceDisabledInvocationFixture {
            private final String name;

            public ForceDisabledInvocationFixture(String name) {
                this.name = name;
            }

            public String greet(String salutation) {
                return salutation + " " + name;
            }
        }
    }
}
