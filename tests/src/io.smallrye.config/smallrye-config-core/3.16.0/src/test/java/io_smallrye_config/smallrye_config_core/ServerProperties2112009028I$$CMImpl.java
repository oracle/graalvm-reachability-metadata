/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_config.smallrye_config_core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.smallrye.config.ConfigMappingClassMapper;
import io.smallrye.config.ConfigMappingContext;
import io.smallrye.config.SmallRyeConfig;

@SuppressWarnings("checkstyle:TypeName") public final class ServerProperties2112009028I$$CMImpl
        implements ServerProperties2112009028I, ConfigMappingClassMapper {
    private final ConfigMappingGeneratorTest.ServerProperties mapped;

    public ServerProperties2112009028I$$CMImpl(final ConfigMappingContext context) {
        SmallRyeConfig config = GeneratedConfigMappingSupport.config(context);
        ConfigMappingGeneratorTest.ServerProperties properties = new ConfigMappingGeneratorTest.ServerProperties();
        String host = config.getRawValue("server.host");
        String port = config.getRawValue("server.http-port");
        String mode = config.getRawValue("server.mode");
        String enabled = config.getRawValue("server.enabled");

        GeneratedConfigMappingSupport.setField(
                ConfigMappingGeneratorTest.ServerProperties.class,
                properties,
                "host",
                host != null ? host : "localhost");
        GeneratedConfigMappingSupport.setField(
                ConfigMappingGeneratorTest.ServerProperties.class,
                properties,
                "port",
                port != null ? Integer.parseInt(port) : 8080);
        GeneratedConfigMappingSupport.setField(
                ConfigMappingGeneratorTest.ServerProperties.class,
                properties,
                "mode",
                mode);
        GeneratedConfigMappingSupport.setField(
                ConfigMappingGeneratorTest.ServerProperties.class,
                properties,
                "enabled",
                enabled != null ? Boolean.parseBoolean(enabled) : Boolean.TRUE);

        GeneratedConfigMappingSupport.markUsed(
                context,
                "server.enabled",
                "server.host",
                "server.http-port",
                "server.mode");

        this.mapped = properties;
    }

    @Override
    public ConfigMappingGeneratorTest.ServerProperties map() {
        return mapped;
    }

    public static Map<String, String> getProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("enabled", "true");
        properties.put("host", "localhost");
        properties.put("http-port", "8080");
        properties.put("mode", null);
        return properties;
    }

    public static Set<String> getSecrets() {
        return Set.of();
    }
}
