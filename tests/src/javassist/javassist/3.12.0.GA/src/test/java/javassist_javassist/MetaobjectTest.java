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
import java.io.Serializable;

import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.Metalevel;
import javassist.tools.reflect.Metaobject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetaobjectTest {
    @Test
    void readsAndWritesPublicInstanceFieldsThroughTrapMethods() {
        ReflectiveFixture fixture = new ReflectiveFixture("initial");
        Metaobject metaobject = new Metaobject(fixture, new Object[0]);
        fixture._setMetaobject(metaobject);

        metaobject.trapFieldWrite("message", "updated");

        assertThat(fixture.message).isEqualTo("updated");
        assertThat(metaobject.trapFieldRead("message")).isEqualTo("updated");
    }

    @Test
    void invokesReflectiveInstanceMethodsThroughTrapMethodcall() throws Throwable {
        ReflectiveFixture fixture = new ReflectiveFixture("receiver");
        Metaobject metaobject = new Metaobject(fixture, new Object[0]);
        fixture._setMetaobject(metaobject);

        Object result = metaobject.trapMethodcall(0, new Object[] {"payload"});

        assertThat(result).isEqualTo("receiver:payload");
        assertThat(metaobject.getMethodName(0)).isEqualTo("join");
        assertThat(metaobject.getParameterTypes(0)).containsExactly(String.class);
        assertThat(metaobject.getReturnType(0)).isEqualTo(String.class);
    }

    @Test
    void serializesBaseObjectAndRestoresReflectiveMethodCache() throws Throwable {
        ReflectiveFixture fixture = new ReflectiveFixture("serialized");
        Metaobject original = new Metaobject(fixture, new Object[0]);
        fixture._setMetaobject(original);

        Metaobject restored = roundTrip(original);

        assertThat(restored.getObject()).isInstanceOf(ReflectiveFixture.class);
        assertThat(restored.trapFieldRead("message")).isEqualTo("serialized");
        assertThat(restored.trapMethodcall(0, new Object[] {"payload"})).isEqualTo("serialized:payload");
    }

    private static Metaobject roundTrip(Metaobject metaobject) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metaobject);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Metaobject) input.readObject();
        }
    }

    public static class ReflectiveFixture implements Metalevel, Serializable {
        public String message;
        private transient ClassMetaobject classMetaobject;
        private transient Metaobject metaobject;

        public ReflectiveFixture(String message) {
            this.message = message;
        }

        @Override
        public ClassMetaobject _getClass() {
            if (classMetaobject == null) {
                classMetaobject = new ClassMetaobject(new String[] {getClass().getName()});
            }
            return classMetaobject;
        }

        @Override
        public Metaobject _getMetaobject() {
            return metaobject;
        }

        @Override
        public void _setMetaobject(Metaobject metaobject) {
            this.metaobject = metaobject;
        }

        public String _m_0_join(String value) {
            return message + ":" + value;
        }
    }
}
