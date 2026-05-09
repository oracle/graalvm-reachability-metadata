/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.Metalevel;
import javassist.tools.reflect.Metaobject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

public class MetaobjectTest {
    @Test
    void metaobjectReadsWritesInvokesAndSerializesBaseObject() throws Throwable {
        SampleReflectiveObject target = new SampleReflectiveObject("initial");
        Metaobject metaobject = new Metaobject(target, new Object[] { "initial" });
        target._setMetaobject(metaobject);

        assertThat(metaobject.getObject()).isSameAs(target);
        assertThat(metaobject.getClassMetaobject().getJavaClass()).isSameAs(SampleReflectiveObject.class);
        assertThat(metaobject.trapFieldRead("value")).isEqualTo("initial");

        metaobject.trapFieldWrite("value", "updated");

        assertThat(target.value).isEqualTo("updated");
        assertThat(metaobject.trapMethodcall(0, new Object[] { "echo" })).isEqualTo("echo:updated");

        Metaobject deserialized = roundTrip(metaobject);

        assertThat(deserialized.getObject()).isInstanceOf(SampleReflectiveObject.class);
        assertThat(deserialized.getClassMetaobject().getJavaClass()).isSameAs(SampleReflectiveObject.class);
        assertThat(deserialized.trapFieldRead("value")).isEqualTo("updated");
        assertThat(deserialized.trapMethodcall(0, new Object[] { "again" })).isEqualTo("again:updated");
    }

    private static Metaobject roundTrip(Metaobject metaobject) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            outputStream.writeObject(metaobject);
        }

        byte[] serialized = byteArrayOutputStream.toByteArray();
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (Metaobject) inputStream.readObject();
        }
    }

    public static final class SampleReflectiveObject implements Metalevel, Serializable {
        private static final ClassMetaobject CLASS_METAOBJECT = new ClassMetaobject(
                new String[] { SampleReflectiveObject.class.getName() });

        public String value;
        private transient Metaobject metaobject;

        public SampleReflectiveObject() {
            this("default");
        }

        public SampleReflectiveObject(String value) {
            this.value = value;
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

        public String _m_0_formatValue(String prefix) {
            return prefix + ":" + value;
        }
    }
}
