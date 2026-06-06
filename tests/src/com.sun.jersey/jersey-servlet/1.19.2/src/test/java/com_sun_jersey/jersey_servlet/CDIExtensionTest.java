/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.server.impl.cdi.CDIExtension;
import com.sun.jersey.server.impl.cdi.DiscoveredParameter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.ObserverMethod;
import javax.ws.rs.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CDIExtensionTest {
    @Test
    void afterBeanDiscoveryRecognizesDiscoveredContextGenericArrayTypes() throws Exception {
        CDIExtension extension = new CDIExtension();
        DiscoveredParameter parameter = new DiscoveredParameter(
                new ContextAnnotation(), new GenericArrayClassType(String.class), null, false);
        RecordingAfterBeanDiscovery event = new RecordingAfterBeanDiscovery();

        setField(extension, "discoveredParameterMap", Collections.singletonMap(Context.class,
                Collections.singleton(parameter)));
        setField(extension, "staticallyDefinedContextBeans", Collections.singleton(String[].class));

        Method afterBeanDiscovery = CDIExtension.class.getDeclaredMethod(
                "afterBeanDiscovery", AfterBeanDiscovery.class);
        afterBeanDiscovery.setAccessible(true);
        afterBeanDiscovery.invoke(extension, event);

        assertThat(event.getDefinitionErrors()).isEmpty();
        assertThat(event.getBeans()).hasSize(15);
        assertThat(event.getBeans())
                .allSatisfy(bean -> assertThat(bean.getBeanClass()).isNotEqualTo(String[].class));
    }

    private static void setField(CDIExtension extension, String name, Object value) throws Exception {
        Field field = CDIExtension.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(extension, value);
    }

    private static final class GenericArrayClassType implements GenericArrayType {
        private final Class<?> componentType;

        private GenericArrayClassType(Class<?> componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }

    private static final class ContextAnnotation implements Context {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Context.class;
        }
    }

    private static final class RecordingAfterBeanDiscovery implements AfterBeanDiscovery {
        private final List<Bean<?>> beans = new ArrayList<Bean<?>>();
        private final List<Throwable> definitionErrors = new ArrayList<Throwable>();

        @Override
        public void addDefinitionError(Throwable throwable) {
            definitionErrors.add(throwable);
        }

        @Override
        public void addBean(Bean<?> bean) {
            beans.add(bean);
        }

        @Override
        public void addObserverMethod(ObserverMethod<?> observerMethod) {
            throw new UnsupportedOperationException("Observer methods are not used by this test");
        }

        @Override
        public void addContext(javax.enterprise.context.spi.Context context) {
            throw new UnsupportedOperationException("Contexts are not used by this test");
        }

        @Override
        public <T> AnnotatedType<T> getAnnotatedType(Class<T> type, String id) {
            throw new UnsupportedOperationException("Annotated types are not used by this test");
        }

        @Override
        public <T> Iterable<AnnotatedType<T>> getAnnotatedTypes(Class<T> type) {
            throw new UnsupportedOperationException("Annotated types are not used by this test");
        }

        private List<Bean<?>> getBeans() {
            return beans;
        }

        private List<Throwable> getDefinitionErrors() {
            return definitionErrors;
        }
    }
}
