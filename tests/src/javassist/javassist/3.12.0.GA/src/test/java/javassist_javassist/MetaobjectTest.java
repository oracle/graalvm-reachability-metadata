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
import java.io.Serializable;

import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.Metalevel;
import javassist.tools.reflect.Metaobject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetaobjectTest {
    @Test
    void metaobjectDispatchesFieldMethodAndSerializationOperations() throws Throwable {
        Metaobject metaobject = new Metaobject(new ReflectiveBase("initial", 3), new Object[0]);

        assertThat(metaobject.getClassMetaobject().getJavaClass()).isSameAs(ReflectiveBase.class);
        assertThat(metaobject.trapFieldRead("message")).isEqualTo("initial");

        metaobject.trapFieldWrite("message", "changed");
        assertThat(metaobject.trapFieldRead("message")).isEqualTo("changed");

        Object methodResult = metaobject.trapMethodcall(1, new Object[] { "-suffix" });
        assertThat(methodResult).isEqualTo("changed-suffix:4");
        assertThat(metaobject.getMethodName(1)).isEqualTo("append");
        assertThat(metaobject.getParameterTypes(1)).containsExactly(String.class);
        assertThat(metaobject.getReturnType(1)).isSameAs(String.class);

        Metaobject deserialized = roundTrip(metaobject);
        assertThat(deserialized.trapFieldRead("message")).isEqualTo("changed-suffix");
        assertThat(deserialized.trapMethodcall(1, new Object[] { "-again" })).isEqualTo("changed-suffix-again:5");

        ReflectiveBase replacement = new ReflectiveBase("replacement", 7);
        deserialized.setObject(replacement);

        assertThat(deserialized.getObject()).isSameAs(replacement);
        assertThat(replacement._getMetaobject()).isSameAs(deserialized);
        assertThat(deserialized.trapFieldRead("message")).isEqualTo("replacement");
    }

    private static Metaobject roundTrip(Metaobject metaobject) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metaobject);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Metaobject) input.readObject();
        }
    }

    public static class ReflectiveBase implements Metalevel, Serializable {
        private static final long serialVersionUID = 1L;
        private static final ClassMetaobject CLASS_METAOBJECT = new ClassMetaobject(
                new String[] { ReflectiveBase.class.getName() });

        public String message;
        public int count;
        private transient Metaobject metaobject;

        public ReflectiveBase(String message, int count) {
            this.message = message;
            this.count = count;
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

        public String _m_1_append(String suffix) {
            count++;
            message += suffix;
            return message + ":" + count;
        }
    }
}
