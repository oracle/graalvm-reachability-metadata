/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.reflection.ReflectionHelper;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionHelperTest {
    @BeforeAll
    static void setRuntimeDelegate() {
        RuntimeDelegate.setInstance(new MinimalRuntimeDelegate());
    }

    @Test
    void classLookupUsesExplicitLoaderAndFallbackLoader() throws Exception {
        String mediaTypeName = MediaType.class.getName();
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();

        assertThat(ReflectionHelper.classForName(mediaTypeName, classLoader))
                .isEqualTo(MediaType.class);
        assertThat(ReflectionHelper.classForName(mediaTypeName, null))
                .isEqualTo(MediaType.class);
        assertThat(ReflectionHelper.classForNameWithException(mediaTypeName, classLoader))
                .isEqualTo(MediaType.class);
        assertThat(ReflectionHelper.classForNameWithException(mediaTypeName, null))
                .isEqualTo(MediaType.class);
    }

    @Test
    void helperFindsStringBasedFactoryMethodsAndConstructor() {
        Method valueOf = ReflectionHelper.getValueOfStringMethod(MediaType.class);
        Method fromString = ReflectionHelper.getFromStringStringMethod(HeaderDelegate.class);
        Constructor<?> constructor = ReflectionHelper.getStringConstructor(EntityTag.class);

        assertThat(valueOf).isNotNull();
        assertThat(valueOf.getName()).isEqualTo("valueOf");
        assertThat(valueOf.getReturnType()).isEqualTo(MediaType.class);
        assertThat(fromString).isNotNull();
        assertThat(fromString.getName()).isEqualTo("fromString");
        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(EntityTag.class);
    }

    @Test
    void helperCreatesArrayClass() {
        assertThat(ReflectionHelper.getArrayClass(MediaType.class)).isEqualTo(MediaType[].class);
    }

    @Test
    void findMethodOnClassUsesExactPublicMethodAndGenericFallback() throws Exception {
        Method getType = MediaType.class.getMethod("getType");
        Method putSingle = MultivaluedMapImpl.class.getMethod("putSingle", String.class, String.class);

        assertThat(ReflectionHelper.findMethodOnClass(MediaType.class, getType)).isEqualTo(getType);
        Method genericPutSingle = ReflectionHelper.findMethodOnClass(MultivaluedMap.class, putSingle);
        assertThat(genericPutSingle).isNotNull();
        assertThat(genericPutSingle.getDeclaringClass()).isEqualTo(MultivaluedMap.class);
        assertThat(genericPutSingle.getName()).isEqualTo("putSingle");
    }

    private static final class MinimalRuntimeDelegate extends RuntimeDelegate {
        @Override
        public UriBuilder createUriBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResponseBuilder createResponseBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public VariantListBuilder createVariantListBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T createEndpoint(Application application, Class<T> endpointType) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            return (HeaderDelegate<T>) NoOpHeaderDelegate.INSTANCE;
        }
    }

    private enum NoOpHeaderDelegate implements HeaderDelegate<Object> {
        INSTANCE;

        @Override
        public Object fromString(String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString(Object value) {
            throw new UnsupportedOperationException();
        }
    }
}
