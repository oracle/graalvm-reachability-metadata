/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.tools.reflect.ClassMetaobject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

public class ClassMetaobjectTest {
    @Test
    void classMetaobjectInvokesConstructorsFieldsMethodsAndSerialization() throws Throwable {
        boolean originalUseContextClassLoader = ClassMetaobject.useContextClassLoader;
        try {
            ClassMetaobject.useContextClassLoader = false;
            SampleTarget.staticValue = "initial";

            ClassMetaobject metaobject = new ClassMetaobject(new String[] {SampleTarget.class.getName()});

            assertThat(metaobject.getJavaClass()).isSameAs(SampleTarget.class);
            assertThat(metaobject.newInstance(new Object[0])).isInstanceOf(SampleTarget.class);
            assertThat(metaobject.trapFieldRead("staticValue")).isEqualTo("initial");

            metaobject.trapFieldWrite("staticValue", "updated");

            assertThat(SampleTarget.staticValue).isEqualTo("updated");
            assertThat(ClassMetaobject.invoke(new SampleTarget(), 7, new Object[] {"value"}))
                    .isEqualTo("instance:value");
            assertThat(metaobject.trapMethodcall(0, new Object[] {"value"})).isEqualTo("static:value");

            ClassMetaobject deserialized = roundTrip(metaobject);

            assertThat(deserialized.getJavaClass()).isSameAs(SampleTarget.class);
            assertThat(deserialized.newInstance(new Object[0])).isInstanceOf(SampleTarget.class);
        } finally {
            ClassMetaobject.useContextClassLoader = originalUseContextClassLoader;
        }
    }

    @Test
    void classMetaobjectCanResolveClassesWithContextClassLoader() {
        boolean originalUseContextClassLoader = ClassMetaobject.useContextClassLoader;
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            ClassMetaobject.useContextClassLoader = true;
            thread.setContextClassLoader(SampleTarget.class.getClassLoader());

            ClassMetaobject metaobject = new ClassMetaobject(new String[] {SampleTarget.class.getName()});

            assertThat(metaobject.getJavaClass()).isSameAs(SampleTarget.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
            ClassMetaobject.useContextClassLoader = originalUseContextClassLoader;
        }
    }

    private static ClassMetaobject roundTrip(ClassMetaobject metaobject) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            outputStream.writeObject(metaobject);
        }

        byte[] serialized = byteArrayOutputStream.toByteArray();
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (ClassMetaobject) inputStream.readObject();
        }
    }

    public static class SampleTarget {
        public static String staticValue;

        public String _m_7_instanceEcho(String value) {
            return "instance:" + value;
        }

        public static String _m_0_staticEcho(String value) {
            return "static:" + value;
        }
    }
}
