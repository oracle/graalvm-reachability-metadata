/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.junit.jupiter.api.Test;

import org.jboss.as.server.deployment.module.DelegatingClassFileTransformer;
import org.jboss.modules.JLIClassTransformer;
import org.springframework.instrument.classloading.jboss.JBossLoadTimeWeaver;

public class JBossLoadTimeWeaverTest {

    @Test
    void jbossLoadTimeWeaverUnwrapsDelegatingTransformerAndAddsTransformer() {
        DelegatingClassFileTransformer delegatingTransformer = new DelegatingClassFileTransformer();
        TestJBossClassLoader classLoader = new TestJBossClassLoader(getClass().getClassLoader(),
                new JLIClassTransformer(delegatingTransformer));

        JBossLoadTimeWeaver weaver = new JBossLoadTimeWeaver(classLoader);

        assertThat(weaver.getInstrumentableClassLoader()).isSameAs(classLoader);

        ClassFileTransformer transformer = new TestClassFileTransformer();
        weaver.addTransformer(transformer);

        assertThat(delegatingTransformer.getTransformers()).containsExactly(transformer);
        assertThat(weaver.getThrowawayClassLoader()).isNotNull().isNotSameAs(classLoader);
    }

    private static final class TestClassFileTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            return null;
        }
    }

    public static final class TestJBossClassLoader extends ClassLoader {

        @SuppressWarnings("unused")
        private final JLIClassTransformer transformer;

        TestJBossClassLoader(ClassLoader parent, JLIClassTransformer transformer) {
            super(parent);
            this.transformer = transformer;
        }
    }
}
