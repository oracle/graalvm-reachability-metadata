/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.instrument.ClassFileTransformer;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.context.annotation.EnableLoadTimeWeaving.AspectJWeaving;
import org.springframework.context.annotation.LoadTimeWeavingConfiguration;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.instrument.classloading.LoadTimeWeaver;

public class LoadTimeWeavingConfigurationTest {

    @Test
    void autodetectAspectJWeavingChecksForAopXmlResource() {
        RecordingClassLoader classLoader = new RecordingClassLoader(getClass().getClassLoader());
        TestLoadTimeWeaver loadTimeWeaver = new TestLoadTimeWeaver(classLoader);
        LoadTimeWeavingConfiguration configuration = new LoadTimeWeavingConfiguration();
        configuration.setBeanClassLoader(classLoader);
        configuration.setImportMetadata(
                AnnotationMetadata.introspect(AutodetectLoadTimeWeavingConfiguration.class));
        configuration.setLoadTimeWeavingConfigurer(() -> loadTimeWeaver);

        LoadTimeWeaver configuredLoadTimeWeaver = configuration.loadTimeWeaver();

        assertSame(loadTimeWeaver, configuredLoadTimeWeaver);
        assertEquals(1, classLoader.getAspectJAopXmlResourceLookups());
    }

    @Configuration
    @EnableLoadTimeWeaving(aspectjWeaving = AspectJWeaving.AUTODETECT)
    public static class AutodetectLoadTimeWeavingConfiguration {
    }

    private static final class RecordingClassLoader extends ClassLoader {

        private final AtomicInteger aspectJAopXmlResourceLookups = new AtomicInteger();

        private RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE.equals(name)) {
                this.aspectJAopXmlResourceLookups.incrementAndGet();
                return null;
            }
            return super.getResource(name);
        }

        private int getAspectJAopXmlResourceLookups() {
            return this.aspectJAopXmlResourceLookups.get();
        }
    }

    private static final class TestLoadTimeWeaver implements LoadTimeWeaver {

        private final ClassLoader classLoader;

        private TestLoadTimeWeaver(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            throw new UnsupportedOperationException("AspectJ weaving should not be enabled without aop.xml");
        }

        @Override
        public ClassLoader getInstrumentableClassLoader() {
            return this.classLoader;
        }

        @Override
        public ClassLoader getThrowawayClassLoader() {
            return this.classLoader;
        }
    }
}
