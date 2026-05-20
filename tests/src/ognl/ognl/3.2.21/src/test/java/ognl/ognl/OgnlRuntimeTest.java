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
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
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
        assertThat(OgnlRuntime.getStaticField(context, FieldFixture.class.getName(), "KIND")).isEqualTo("field-fixture");
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
    void invokesMethodThroughOgnlSandboxWhenTheRuntimeAllowsSecurityManagerInstallation() throws Exception {
        final OgnlContext context = newContext();
        final InvocationFixture fixture = new InvocationFixture("Grace");
        final String previousValue = System.getProperty("ognl.security.manager");
        System.setProperty("ognl.security.manager", "true");
        try {
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
        } finally {
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

    private static final class AllowAllMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return true;
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

    public static final class MethodDiscoveryFixture {
        public String instanceMessage() {
            return "instance";
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
}
