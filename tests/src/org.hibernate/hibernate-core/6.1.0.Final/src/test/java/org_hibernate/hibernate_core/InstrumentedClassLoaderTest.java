/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.bytecode.spi.ClassTransformer;
import org.hibernate.bytecode.spi.InstrumentedClassLoader;
import org.junit.jupiter.api.Test;

import java.security.ProtectionDomain;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentedClassLoaderTest {

    @Test
    public void delegatesPlatformAndUnchangedHibernateClasses() throws Exception {
        RecordingTransformer transformer = new RecordingTransformer();
        ClassLoader parent = Session.class.getClassLoader();
        InstrumentedClassLoader classLoader = new InstrumentedClassLoader(parent, transformer);

        assertThat(classLoader.loadClass(String.class.getName())).isSameAs(String.class);
        assertThat(transformer.className).isNull();

        Class<?> loadedClass = classLoader.loadClass(Session.class.getName());

        assertThat(loadedClass).isSameAs(Session.class);
        assertThat(transformer.loader).isSameAs(parent);
        assertThat(transformer.className).isEqualTo(Session.class.getName());
        assertThat(transformer.bytecodeLength).isPositive();
    }

    private static final class RecordingTransformer implements ClassTransformer {
        private ClassLoader loader;
        private String className;
        private int bytecodeLength;

        @Override
        public byte[] transform(
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer) {
            this.loader = loader;
            this.className = className;
            this.bytecodeLength = classfileBuffer.length;
            return null;
        }
    }
}
