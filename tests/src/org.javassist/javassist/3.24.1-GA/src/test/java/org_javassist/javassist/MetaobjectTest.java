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
import java.io.Serializable;

import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.Metaobject;
import javassist.tools.reflect.Metalevel;

import org.junit.jupiter.api.Test;

public class MetaobjectTest {
    @Test
    void trapsInstanceFieldsMethodsAndRestoresAfterSerialization() throws Throwable {
        ReflectiveObject target = new ReflectiveObject("Ada");
        Metaobject metaobject = target._getMetaobject();

        metaobject.trapFieldWrite("publicLabel", "Grace");
        assertThat(metaobject.trapFieldRead("publicLabel")).isEqualTo("Grace");

        int describeMethod = metaobject.getClassMetaobject()
                .getMethodIndex("describe", new Class<?>[] {String.class});
        assertThat(metaobject.getMethodName(describeMethod)).isEqualTo("describe");
        assertThat(metaobject.getParameterTypes(describeMethod)).containsExactly(String.class);
        assertThat(metaobject.getReturnType(describeMethod)).isEqualTo(String.class);
        assertThat(metaobject.trapMethodcall(describeMethod, new Object[] {"Dr"})).isEqualTo("Dr Grace");

        Metaobject restored = deserialize(serialize(metaobject));

        assertThat(restored.getObject()).isInstanceOf(ReflectiveObject.class);
        assertThat(restored.trapFieldRead("publicLabel")).isEqualTo("Grace");
        restored.trapFieldWrite("publicLabel", "Linus");
        assertThat(restored.trapMethodcall(describeMethod, new Object[] {"Mx"})).isEqualTo("Mx Linus");
    }

    private static byte[] serialize(Metaobject metaobject) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(metaobject);
        }
        return bytes.toByteArray();
    }

    private static Metaobject deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Metaobject) input.readObject();
        }
    }

    public static class ReflectiveObject implements Metalevel, Serializable {
        private static final long serialVersionUID = 1L;
        private static final ClassMetaobject CLASS_METAOBJECT = new ClassMetaobject(
                new String[] {ReflectiveObject.class.getName()});

        public String publicLabel;
        private transient Metaobject metaobject;

        public ReflectiveObject(String publicLabel) {
            this.publicLabel = publicLabel;
            _setMetaobject(new Metaobject(this, new Object[] {publicLabel}));
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

        public String _m_0_describe(String title) {
            return title + " " + publicLabel;
        }
    }
}
