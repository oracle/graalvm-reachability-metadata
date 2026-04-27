/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javassist.tools.reflect.ClassMetaobject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ClassMetaobjectTest {
    @AfterEach
    void resetClassMetaobjectState() {
        ClassMetaobject.useContextClassLoader = false;
        ReflectiveFixture.staticText = "initial";
    }

    @Test
    void invokesConstructorsFieldsAndReflectiveMethods() throws Throwable {
        ClassMetaobject classMetaobject = new ClassMetaobject(new String[] {ReflectiveFixture.class.getName()});

        Object instance = classMetaobject.newInstance(new Object[] {"Ada"});
        assertThat(classMetaobject.getJavaClass()).isEqualTo(ReflectiveFixture.class);
        assertThat(classMetaobject.isInstance(instance)).isTrue();
        assertThat(((ReflectiveFixture) instance).label()).isEqualTo("Ada");

        classMetaobject.trapFieldWrite("staticText", "updated");
        assertThat(classMetaobject.trapFieldRead("staticText")).isEqualTo("updated");

        int staticMethod = classMetaobject.getMethodIndex("staticJoin", new Class<?>[] {String.class, String.class});
        assertThat(classMetaobject.getMethodName(staticMethod)).isEqualTo("staticJoin");
        assertThat(classMetaobject.getParameterTypes(staticMethod)).containsExactly(String.class, String.class);
        assertThat(classMetaobject.getReturnType(staticMethod)).isEqualTo(String.class);
        assertThat(classMetaobject.trapMethodcall(staticMethod, new Object[] {"left", "right"}))
                .isEqualTo("left:right:updated");

        assertThat(ClassMetaobject.invoke(instance, 1, new Object[] {"Grace"})).isEqualTo("Ada->Grace");
    }

    @Test
    void restoresConstructorsAfterSerializationWithContextClassLoader() throws Exception {
        ClassMetaobject.useContextClassLoader = true;
        ClassMetaobject original = new ClassMetaobject(new String[] {ReflectiveFixture.class.getName()});

        ClassMetaobject restored = deserialize(serialize(original));

        Object instance = restored.newInstance(new Object[] {"Linus"});
        assertThat(restored.getName()).isEqualTo(ReflectiveFixture.class.getName());
        assertThat(((ReflectiveFixture) instance).label()).isEqualTo("Linus");
    }

    private static byte[] serialize(ClassMetaobject classMetaobject) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(classMetaobject);
        }
        return bytes.toByteArray();
    }

    private static ClassMetaobject deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ClassMetaobject) input.readObject();
        }
    }

    public static class ReflectiveFixture {
        public static String staticText = "initial";

        private final String label;

        public ReflectiveFixture(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static String _m_0_staticJoin(String first, String second) {
            return first + ":" + second + ":" + staticText;
        }

        public String _m_1_rename(String replacement) {
            return label + "->" + replacement;
        }
    }
}
