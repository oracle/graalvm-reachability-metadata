/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.CannotCreateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassMetaobjectTest {
    @Test
    void loadsClassWithForNameAndContextClassLoader() {
        boolean previousUseContextClassLoader = ClassMetaobject.useContextClassLoader;
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassMetaobject.useContextClassLoader = false;
            ClassMetaobject forNameMetaobject = new ClassMetaobject(new String[] {ConstructorFixture.class.getName()});
            assertThat(forNameMetaobject.getJavaClass()).isEqualTo(ConstructorFixture.class);
            assertThat(forNameMetaobject.getName()).isEqualTo(ConstructorFixture.class.getName());

            Thread.currentThread().setContextClassLoader(ClassMetaobjectTest.class.getClassLoader());
            ClassMetaobject.useContextClassLoader = true;
            ClassMetaobject contextLoaderMetaobject = new ClassMetaobject(
                    new String[] {StaticMemberFixture.class.getName()});
            assertThat(contextLoaderMetaobject.getJavaClass()).isEqualTo(StaticMemberFixture.class);
            assertThat(contextLoaderMetaobject.isInstance(new StaticMemberFixture())).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            ClassMetaobject.useContextClassLoader = previousUseContextClassLoader;
        }
    }

    @Test
    void deserializesClassMetaobjectAndRebuildsConstructorCache()
            throws IOException, ClassNotFoundException, CannotCreateException {
        ClassMetaobject original = metaobjectFor(ConstructorFixture.class);

        ClassMetaobject restored = roundTrip(original);
        Object instance = restored.newInstance(new Object[] {"restored", Integer.valueOf(7)});

        assertThat(restored.getJavaClass()).isEqualTo(ConstructorFixture.class);
        assertThat(instance).isInstanceOf(ConstructorFixture.class);
        assertThat(((ConstructorFixture) instance).description()).isEqualTo("restored:7");
    }

    @Test
    void createsInstancesWithPublicConstructors() throws CannotCreateException {
        ClassMetaobject metaobject = metaobjectFor(ConstructorFixture.class);

        Object instance = metaobject.newInstance(new Object[] {"created", Integer.valueOf(3)});

        assertThat(instance).isInstanceOf(ConstructorFixture.class);
        assertThat(((ConstructorFixture) instance).description()).isEqualTo("created:3");
    }

    @Test
    void readsAndWritesPublicStaticFieldsThroughTrapMethods() {
        ClassMetaobject metaobject = metaobjectFor(StaticMemberFixture.class);

        StaticMemberFixture.status = "before";
        metaobject.trapFieldWrite("status", "after");

        assertThat(StaticMemberFixture.status).isEqualTo("after");
        assertThat(metaobject.trapFieldRead("status")).isEqualTo("after");
    }

    @Test
    void invokesInstanceAndStaticReflectiveMethodTraps() throws Throwable {
        InstanceMethodFixture target = new InstanceMethodFixture("receiver");

        Object instanceResult = ClassMetaobject.invoke(target, 0, new Object[] {"payload"});
        ClassMetaobject staticMetaobject = metaobjectFor(StaticMemberFixture.class);
        Object staticResult = staticMetaobject.trapMethodcall(0, new Object[] {"payload"});

        assertThat(instanceResult).isEqualTo("receiver:payload");
        assertThat(staticResult).isEqualTo("static:payload");
        assertThat(staticMetaobject.getMethodName(0)).isEqualTo("staticEcho");
        assertThat(staticMetaobject.getParameterTypes(0)).containsExactly(String.class);
        assertThat(staticMetaobject.getReturnType(0)).isEqualTo(String.class);
        assertThat(staticMetaobject.getMethodIndex("staticEcho", new Class[] {String.class})).isEqualTo(0);
    }

    private static ClassMetaobject metaobjectFor(Class<?> type) {
        boolean previousUseContextClassLoader = ClassMetaobject.useContextClassLoader;
        try {
            ClassMetaobject.useContextClassLoader = false;
            return new ClassMetaobject(new String[] {type.getName()});
        } finally {
            ClassMetaobject.useContextClassLoader = previousUseContextClassLoader;
        }
    }

    private static ClassMetaobject roundTrip(ClassMetaobject metaobject) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metaobject);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ClassMetaobject) input.readObject();
        }
    }

    public static class ConstructorFixture {
        private final String name;
        private final Integer count;

        public ConstructorFixture(String name, Integer count) {
            this.name = name;
            this.count = count;
        }

        String description() {
            return name + ":" + count;
        }
    }

    public static class StaticMemberFixture {
        public static String status = "initial";

        public static String _m_0_staticEcho(String value) {
            return "static:" + value;
        }
    }

    public static class InstanceMethodFixture {
        private final String prefix;

        public InstanceMethodFixture(String prefix) {
            this.prefix = prefix;
        }

        public String _m_0_join(String value) {
            return prefix + ":" + value;
        }
    }
}
