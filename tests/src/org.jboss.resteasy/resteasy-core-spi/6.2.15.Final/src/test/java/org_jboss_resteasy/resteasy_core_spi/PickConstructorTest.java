/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;

import org.jboss.resteasy.spi.util.PickConstructor;
import org.junit.jupiter.api.Test;

public class PickConstructorTest {
    @Test
    void singletonConstructorPrefersPublicConstructorWhoseParametersAreContexts() {
        Constructor<?> constructor = PickConstructor.pickSingletonConstructor(SingletonProvider.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(Object.class, String.class);
    }

    @Test
    void perRequestConstructorPrefersPublicConstructorWhoseParametersHaveJaxrsAnnotations() {
        Constructor<?> constructor = PickConstructor.pickPerRequestConstructor(PerRequestResource.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, String.class);
        assertThat(constructor.getParameterAnnotations()[0][0]).isInstanceOf(PathParam.class);
        assertThat(constructor.getParameterAnnotations()[1][0]).isInstanceOf(HeaderParam.class);
    }

    public static class SingletonProvider {
        public SingletonProvider() {
        }

        public SingletonProvider(String name) {
        }

        public SingletonProvider(@Context Object requestContext, @Context String configuration) {
        }
    }

    public static class PerRequestResource {
        public PerRequestResource() {
        }

        public PerRequestResource(Object name) {
        }

        public PerRequestResource(@QueryParam("id") String id) {
        }

        public PerRequestResource(@PathParam("id") String id, @HeaderParam("agent") String agent) {
        }

        private PerRequestResource(@QueryParam("secret") int secret) {
        }
    }
}
