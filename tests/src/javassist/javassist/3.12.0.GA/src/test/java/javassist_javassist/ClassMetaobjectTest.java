/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javassist.tools.reflect.ClassMetaobject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMetaobjectTest {
    @Test
    void classMetaobjectDispatchesToConstructorsFieldsAndMethods() throws Throwable {
        boolean originalUseContextClassLoader = ClassMetaobject.useContextClassLoader;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassMetaobject.useContextClassLoader = false;
            ClassMetaobject classMetaobject = new ClassMetaobject(new String[] { ReflectiveFixture.class.getName() });

            assertThat(classMetaobject.getJavaClass()).isSameAs(ReflectiveFixture.class);
            assertThat(classMetaobject.getName()).isEqualTo(ReflectiveFixture.class.getName());

            ReflectiveFixture constructed = (ReflectiveFixture) classMetaobject.newInstance(new Object[] { "base", 4 });
            assertThat(classMetaobject.isInstance(constructed)).isTrue();
            assertThat(constructed.describe()).isEqualTo("base:4");

            classMetaobject.trapFieldWrite("sharedValue", "changed");
            assertThat(classMetaobject.trapFieldRead("sharedValue")).isEqualTo("changed");

            Object instanceResult = ClassMetaobject.invoke(constructed, 7, new Object[] { 6 });
            assertThat(instanceResult).isEqualTo(10);
            assertThat(constructed.describe()).isEqualTo("base:10");

            Object staticResult = classMetaobject.trapMethodcall(0, new Object[] { "left", "right" });
            assertThat(staticResult).isEqualTo("left:right:changed");
            assertThat(classMetaobject.getMethodName(0)).isEqualTo("join");
            assertThat(classMetaobject.getParameterTypes(0)).containsExactly(String.class, String.class);
            assertThat(classMetaobject.getReturnType(0)).isSameAs(String.class);
            assertThat(classMetaobject.getMethodIndex("join", new Class[] { String.class, String.class })).isZero();
            assertThat(classMetaobject.getMethod(0).getName()).isEqualTo("_m_0_join");

            ClassMetaobject deserialized = roundTrip(classMetaobject);
            ReflectiveFixture defaultConstructed = (ReflectiveFixture) deserialized.newInstance(new Object[0]);
            assertThat(defaultConstructed.describe()).isEqualTo("default:1");

            ClassMetaobject.useContextClassLoader = true;
            Thread.currentThread().setContextClassLoader(ReflectiveFixture.class.getClassLoader());
            ClassMetaobject contextLoaded = new ClassMetaobject(new String[] { ReflectiveFixture.class.getName() });
            assertThat(contextLoaded.getJavaClass()).isSameAs(ReflectiveFixture.class);
        } finally {
            ClassMetaobject.useContextClassLoader = originalUseContextClassLoader;
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            ReflectiveFixture.sharedValue = "initial";
        }
    }

    private static ClassMetaobject roundTrip(ClassMetaobject classMetaobject) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(classMetaobject);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ClassMetaobject) input.readObject();
        }
    }

    public static class ReflectiveFixture {
        public static String sharedValue = "initial";

        private String prefix;
        private int value;

        public ReflectiveFixture() {
            this("default", 1);
        }

        public ReflectiveFixture(String prefix, int value) {
            this.prefix = prefix;
            this.value = value;
        }

        public String describe() {
            return prefix + ":" + value;
        }

        public int _m_7_increment(int delta) {
            value += delta;
            return value;
        }

        public static String _m_0_join(String left, String right) {
            return left + ":" + right + ":" + sharedValue;
        }
    }
}
