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
import java.io.Serializable;

import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.Metaobject;
import javassist.tools.reflect.Metalevel;

import org.junit.jupiter.api.Test;

public class MetaobjectTest {
    @Test
    void readsAndWritesPublicInstanceFields() {
        ReflectiveInstance fixture = new ReflectiveInstance("initial");
        Metaobject metaobject = new Metaobject(fixture, new Object[0]);

        assertThat(metaobject.trapFieldRead("message")).isEqualTo("initial");

        metaobject.trapFieldWrite("message", "updated");

        assertThat(fixture.message).isEqualTo("updated");
        assertThat(metaobject.trapFieldRead("message")).isEqualTo("updated");
    }

    @Test
    void invokesReflectiveInstanceMethods() throws Throwable {
        ReflectiveInstance fixture = new ReflectiveInstance("receiver");
        Metaobject metaobject = new Metaobject(fixture, new Object[0]);

        Object result = metaobject.trapMethodcall(0, new Object[] {"argument"});

        assertThat(result).isEqualTo("receiver:argument");
        assertThat(metaobject.getMethodName(0)).isEqualTo("echo");
        assertThat(metaobject.getParameterTypes(0)).containsExactly(String.class);
        assertThat(metaobject.getReturnType(0)).isSameAs(String.class);
    }

    @Test
    void restoresMetaobjectStateFromSerializedBaseObject() throws Throwable {
        ReflectiveInstance fixture = new ReflectiveInstance("serialized");
        Metaobject metaobject = new Metaobject(fixture, new Object[0]);

        Metaobject deserialized = serializeAndDeserialize(metaobject);

        assertThat(deserialized.getObject()).isInstanceOf(ReflectiveInstance.class);
        assertThat(deserialized.getClassMetaobject().getJavaClass()).isSameAs(ReflectiveInstance.class);
        assertThat(deserialized.trapFieldRead("message")).isEqualTo("serialized");
        assertThat(deserialized.trapMethodcall(0, new Object[] {"call"}))
                .isEqualTo("serialized:call");
    }

    private static Metaobject serializeAndDeserialize(Metaobject metaobject) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metaobject);
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(stream)) {
            return (Metaobject) input.readObject();
        }
    }

    public static class ReflectiveInstance implements Metalevel, Serializable {
        private static final ClassMetaobject CLASS_METAOBJECT =
                new ClassMetaobject(new String[] {ReflectiveInstance.class.getName()});

        public String message;

        private transient Metaobject metaobject;

        public ReflectiveInstance(String message) {
            this.message = message;
        }

        @Override
        public ClassMetaobject _getClass() {
            return CLASS_METAOBJECT;
        }

        @Override
        public Metaobject _getMetaobject() {
            return metaobject;
        }

        @Override
        public void _setMetaobject(Metaobject metaobject) {
            this.metaobject = metaobject;
        }

        public String _m_0_echo(String value) {
            return message + ":" + value;
        }
    }
}
