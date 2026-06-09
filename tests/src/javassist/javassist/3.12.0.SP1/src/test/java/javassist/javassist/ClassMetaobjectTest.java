/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

import javassist.tools.reflect.ClassMetaobject;

import org.junit.jupiter.api.Test;

public class ClassMetaobjectTest {
    @Test
    void constructsWithBothClassLoadingModesAndRestoresFromSerialization() throws Exception {
        boolean originalUseContextClassLoader = ClassMetaobject.useContextClassLoader;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassMetaobject.useContextClassLoader = false;
            ClassMetaobject metaobject = newReflectiveFixtureMetaobject();

            assertThat(metaobject.getJavaClass()).isSameAs(ReflectiveFixture.class);
            assertThat(metaobject.getName()).isEqualTo(ReflectiveFixture.class.getName());
            assertThat(metaobject.isInstance(new ReflectiveFixture())).isTrue();

            ClassMetaobject deserialized = serializeAndDeserialize(metaobject);
            assertThat(deserialized.getJavaClass()).isSameAs(ReflectiveFixture.class);

            ClassMetaobject.useContextClassLoader = true;
            Thread.currentThread()
                    .setContextClassLoader(new FixtureClassLoader(originalContextClassLoader));
            ClassMetaobject contextLoaded = newReflectiveFixtureMetaobject();

            assertThat(contextLoaded.getJavaClass()).isSameAs(ReflectiveFixture.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            ClassMetaobject.useContextClassLoader = originalUseContextClassLoader;
        }
    }

    @Test
    void createsInstancesAndReadsAndWritesStaticFields() throws Exception {
        ClassMetaobject metaobject = newReflectiveFixtureMetaobject();

        Object created = metaobject.newInstance(new Object[] {"constructed"});
        assertThat(created).isInstanceOf(ReflectiveFixture.class);
        assertThat(((ReflectiveFixture) created).message).isEqualTo("constructed");

        metaobject.trapFieldWrite("staticMessage", "updated");
        assertThat(metaobject.trapFieldRead("staticMessage")).isEqualTo("updated");
    }

    @Test
    void invokesInstanceAndStaticReflectiveMethods() throws Throwable {
        ReflectiveFixture fixture = new ReflectiveFixture("target");

        Object instanceResult = ClassMetaobject.invoke(fixture, 0, new Object[] {"call"});
        assertThat(instanceResult).isEqualTo("target:call");

        ClassMetaobject metaobject = newReflectiveFixtureMetaobject();
        Object staticResult = metaobject.trapMethodcall(1, new Object[] {"left", "right"});
        assertThat(staticResult).isEqualTo("left-right");

        Method staticMethod = metaobject.getMethod(1);
        assertThat(staticMethod.getName()).isEqualTo("_m_1_staticJoin");
        assertThat(metaobject.getMethodName(1)).isEqualTo("staticJoin");
        Class[] stringPair = new Class[] {String.class, String.class};
        assertThat(metaobject.getParameterTypes(1)).containsExactly(stringPair);
        assertThat(metaobject.getReturnType(1)).isSameAs(String.class);
        assertThat(metaobject.getMethodIndex("staticJoin", stringPair)).isEqualTo(1);
    }

    private static ClassMetaobject newReflectiveFixtureMetaobject() {
        return new ClassMetaobject(new String[] {ReflectiveFixture.class.getName()});
    }

    private static ClassMetaobject serializeAndDeserialize(ClassMetaobject metaobject)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metaobject);
        }

        byte[] serialized = bytes.toByteArray();
        ByteArrayInputStream stream = new ByteArrayInputStream(serialized);
        try (ObjectInputStream input = new ObjectInputStream(stream)) {
            return (ClassMetaobject) input.readObject();
        }
    }

    public static class ReflectiveFixture {
        public static String staticMessage = "initial";

        public final String message;

        public ReflectiveFixture() {
            this("default");
        }

        public ReflectiveFixture(String message) {
            this.message = message;
        }

        public String _m_0_instanceEcho(String value) {
            return message + ":" + value;
        }

        public static String _m_1_staticJoin(String first, String second) {
            return first + "-" + second;
        }
    }

    private static final class FixtureClassLoader extends ClassLoader {
        private FixtureClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
