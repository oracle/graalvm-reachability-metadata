/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.pulsar.shade.javassist.tools.reflect.ClassMetaobject;
import org.junit.jupiter.api.Test;

public class ClassMetaobjectTest {
    @Test
    void controlsConstructionFieldsAndRenamedMethods() throws Throwable {
        ClassMetaobject metaobject = new ClassMetaobject(new String[] {ReflectiveTarget.class.getName()});
        ReflectiveTarget target = (ReflectiveTarget) metaobject.newInstance(new Object[] {"target"});

        metaobject.trapFieldWrite("shared", "changed");
        Object instanceResult = ClassMetaobject.invoke(target, 0, new Object[] {"value"});
        Object staticResult = metaobject.trapMethodcall(1, new Object[] {"value"});

        assertThat(metaobject.trapFieldRead("shared")).isEqualTo("changed");
        assertThat(instanceResult).isEqualTo("target:value");
        assertThat(staticResult).isEqualTo("VALUE");
        assertThat(metaobject.getMethodName(0)).isEqualTo("echo");
        assertThat(metaobject.getMethodIndex("upper", new Class<?>[] {String.class})).isEqualTo(1);
    }

    @Test
    void reloadsClassMetadataWithBothSupportedLoadingModes() throws Exception {
        ClassMetaobject direct = new ClassMetaobject(new String[] {ReflectiveTarget.class.getName()});
        ClassMetaobject restored = roundTrip(direct);

        assertThat(restored.getJavaClass()).isEqualTo(ReflectiveTarget.class);
        assertThat(restored.newInstance(new Object[] {"restored"})).isInstanceOf(ReflectiveTarget.class);

        synchronized (ClassMetaobject.class) {
            boolean previous = ClassMetaobject.useContextClassLoader;
            ClassMetaobject.useContextClassLoader = true;
            try {
                ClassMetaobject contextual = new ClassMetaobject(new String[] {ReflectiveTarget.class.getName()});
                assertThat(contextual.getName()).isEqualTo(ReflectiveTarget.class.getName());
            } finally {
                ClassMetaobject.useContextClassLoader = previous;
            }
        }
    }

    private static ClassMetaobject roundTrip(ClassMetaobject value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ClassMetaobject) input.readObject();
        }
    }

    public static class ReflectiveTarget {
        public static String shared = "initial";
        private final String name;

        public ReflectiveTarget(String name) {
            this.name = name;
        }

        public String _m_0_echo(String value) {
            return name + ":" + value;
        }

        public static String _m_1_upper(String value) {
            return value.toUpperCase();
        }
    }
}
