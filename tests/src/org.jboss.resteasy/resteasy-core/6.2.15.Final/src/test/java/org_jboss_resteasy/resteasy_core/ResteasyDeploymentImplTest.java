/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Provider;

import org.jboss.resteasy.core.InjectorFactoryImpl;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResteasyDeploymentImplTest {
    @Test
    void startsDeploymentFromStringBasedConfiguration() {
        ResteasyDeploymentImpl deployment = new ResteasyDeploymentImpl();
        deployment.setRegisterBuiltin(false);
        deployment.setInjectorFactoryClass(InjectorFactoryImpl.class.getName());
        deployment.setApplicationClass(ConfiguredApplication.class.getName());
        deployment.setConstructedDefaultContextObjects(Map.of(
                DefaultContextContract.class.getName(), DefaultContextObject.class.getName()));
        deployment.setScannedProviderClasses(List.of(ConfiguredProvider.class.getName()));
        deployment.setProviderClasses(List.of(ConfiguredProvider.class.getName()));
        deployment.setScannedJndiComponentResources(List.of(jndiResourceConfiguration(ScannedJndiResource.class)));
        deployment.setJndiComponentResources(List.of(jndiResourceConfiguration(ConfiguredJndiResource.class)));
        deployment.setScannedResourceClasses(List.of(ScannedResource.class.getName()));
        deployment.setScannedResourceClassesWithBuilder(Map.of(
                ConfiguredResourceBuilder.class.getName(), List.of(BuiltResource.class.getName())));
        deployment.setResourceClasses(List.of(ConfiguredResource.class.getName()));

        try {
            deployment.start();

            assertThat(deployment.getApplication()).isInstanceOf(ConfiguredApplication.class);
            Object defaultContextObject = deployment.getDefaultContextObjects().get(DefaultContextContract.class);
            assertThat(deployment.getDefaultContextObjects()).containsKey(DefaultContextContract.class);
            assertThat(defaultContextObject).isInstanceOf(DefaultContextObject.class);
            assertThat(deployment.getRegistry()).isNotNull();
            assertThat(deployment.getProviderFactory()).isNotNull();
            assertThat(deployment.getResourceFactories()).hasSize(2);
        } finally {
            deployment.stop();
        }
    }

    private static String jndiResourceConfiguration(Class<?> resourceClass) {
        return "java:global/" + resourceClass.getSimpleName() + ";" + resourceClass.getName() + ";false";
    }

    public interface DefaultContextContract {
    }

    public static class DefaultContextObject implements DefaultContextContract {
        public DefaultContextObject() {
        }
    }

    public static class ConfiguredApplication extends Application {
        public ConfiguredApplication() {
        }

        @Override
        public Map<String, Object> getProperties() {
            return Map.of("jakarta.ws.rs.loadServices", false);
        }
    }

    @Provider
    public static class ConfiguredProvider implements ContainerRequestFilter {
        public ConfiguredProvider() {
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            requestContext.setProperty(ConfiguredProvider.class.getName(), Boolean.TRUE);
        }
    }

    public static class ConfiguredResourceBuilder extends ResourceBuilder {
        public ConfiguredResourceBuilder() {
        }
    }

    @Path("/scanned")
    public static class ScannedResource {
        public ScannedResource() {
        }

        @GET
        public String get() {
            return "scanned";
        }
    }

    @Path("/built")
    public static class BuiltResource {
        public BuiltResource() {
        }

        @GET
        public String get() {
            return "built";
        }
    }

    @Path("/configured")
    public static class ConfiguredResource {
        public ConfiguredResource() {
        }

        @GET
        public String get() {
            return "configured";
        }
    }

    @Path("/scanned-jndi")
    public static class ScannedJndiResource {
        public ScannedJndiResource() {
        }

        @GET
        public String get() {
            return "scanned-jndi";
        }
    }

    @Path("/configured-jndi")
    public static class ConfiguredJndiResource {
        public ConfiguredJndiResource() {
        }

        @GET
        public String get() {
            return "configured-jndi";
        }
    }
}
