/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_core.jersey_server;

import java.io.File;
import java.io.InputStream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.wadl.WadlGenerator;
import org.glassfish.jersey.server.wadl.config.WadlGeneratorConfig;
import org.glassfish.jersey.server.wadl.internal.WadlGeneratorImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WadlGeneratorLoaderTest {
    @Test
    void configuresWadlGeneratorPropertiesUsingApplicationInjectionManager() {
        ConfigurableWadlGenerator.reset();
        WadlGeneratorConfig wadlConfig = WadlGeneratorConfig.generator(ConfigurableWadlGenerator.class)
                .prop("flag", Boolean.TRUE)
                .prop("file", "wadl-generator.xml")
                .prop("stream", "wadl-generator.xml")
                .prop("configuredValue", "configured")
                .build();
        ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class);
        resourceConfig.property(ServerProperties.WADL_GENERATOR_CONFIG, wadlConfig);
        ApplicationHandler handler = new ApplicationHandler(resourceConfig);

        try {
            WadlGenerator generator = wadlConfig.createWadlGenerator(handler.getInjectionManager());

            assertThat(generator).isNotNull();
            assertThat(ConfigurableWadlGenerator.flag).isTrue();
            assertThat(ConfigurableWadlGenerator.delegate).isNotNull();
            assertThat(ConfigurableWadlGenerator.file).isNotNull().hasName("wadl-generator.xml");
            assertThat(ConfigurableWadlGenerator.stream).isNotNull();
            assertThat(ConfigurableWadlGenerator.configuredValue.value).isEqualTo("configured");
        } finally {
            handler.onShutdown(null);
        }
    }

    @Path("greeting")
    public static class HelloResource {
        @GET
        public String greeting() {
            return "hello";
        }
    }

    public static class ConfigurableWadlGenerator extends WadlGeneratorImpl {
        private static WadlGenerator delegate;
        private static Boolean flag;
        private static File file;
        private static InputStream stream;
        private static ConfiguredValue configuredValue;

        public static void reset() {
            delegate = null;
            flag = null;
            file = null;
            stream = null;
            configuredValue = null;
        }

        @Override
        public void setWadlGeneratorDelegate(WadlGenerator value) {
            delegate = value;
        }

        public void setFlag(Boolean value) {
            flag = value;
        }

        public void setFile(File value) {
            file = value;
        }

        public void setStream(InputStream value) {
            stream = value;
        }

        public void setConfiguredValue(ConfiguredValue value) {
            configuredValue = value;
        }
    }

    public static class ConfiguredValue {
        private final String value;

        public ConfiguredValue(String value) {
            this.value = value;
        }
    }
}
