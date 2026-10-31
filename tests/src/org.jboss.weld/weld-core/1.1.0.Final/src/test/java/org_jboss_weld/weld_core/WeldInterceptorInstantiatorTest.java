/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.enterprise.context.spi.CreationalContext;
import javax.inject.Inject;

import org.jboss.interceptor.reader.ClassMetadataInterceptorReference;
import org.jboss.interceptor.reader.ReflectiveClassMetadata;
import org.jboss.interceptor.spi.metadata.InterceptorReference;
import org.jboss.weld.Container;
import org.jboss.weld.bean.interceptor.WeldInterceptorInstantiator;
import org.jboss.weld.bootstrap.api.helpers.SimpleServiceRegistry;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.manager.Enabled;
import org.jboss.weld.manager.InjectionTargetValidator;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.metadata.cache.MetaAnnotationStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.util.reflection.Reflections;
import org.junit.jupiter.api.Test;

public class WeldInterceptorInstantiatorTest {
    @Test
    void constructsUnmanagedInterceptorAndRunsItsInjectionInitializer() {
        SimpleServiceRegistry services = new SimpleServiceRegistry();
        ClassTransformer transformer = new ClassTransformer(new TypeStore());
        services.add(ClassTransformer.class, transformer);
        services.add(MetaAnnotationStore.class, new MetaAnnotationStore(transformer));
        BeanManagerImpl manager = BeanManagerImpl.newRootManager("interceptor-test", services, Enabled.EMPTY_ENABLED);
        Container.initialize(manager, services);
        CreationalContext<TrackingInterceptor> context = manager.createCreationalContext(null);
        try {
            services.add(InjectionTargetValidator.class, new InjectionTargetValidator(manager));
            InterceptorReference<Object> reference = Reflections.cast(ClassMetadataInterceptorReference.of(
                    ReflectiveClassMetadata.of(TrackingInterceptor.class)));
            WeldInterceptorInstantiator<TrackingInterceptor> instantiator =
                    new WeldInterceptorInstantiator<>(manager, context);
            TrackingInterceptor interceptor = instantiator.createFor(reference);
            assertThat(interceptor.constructed).isTrue();
            assertThat(interceptor.initialized).isTrue();
            assertThat(interceptor.intercept("checkout")).isEqualTo("intercepted checkout");
        } finally {
            context.release();
            Container.instance().cleanup();
        }
    }

    public static class TrackingInterceptor {
        private final boolean constructed;
        private boolean initialized;

        public TrackingInterceptor() {
            constructed = true;
        }

        @Inject
        void initialize() {
            initialized = true;
        }

        public String intercept(String value) {
            return "intercepted " + value;
        }
    }
}
