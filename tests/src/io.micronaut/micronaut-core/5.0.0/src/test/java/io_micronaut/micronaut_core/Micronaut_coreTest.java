/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.core.version.VersionUtils;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class Micronaut_coreTest {
    @Test
    void constructsAndUsesResourceResolverThroughInstantiationApi() throws IOException {
        ResourceResolver resolver = InstantiationUtils.instantiate(ResourceResolver.class);

        try (InputStream text =
                        resolver.getResourceAsStream("string:Micronaut Core").orElseThrow();
                InputStream encoded =
                        resolver.getResourceAsStream("base64:bmF0aXZlLWltYWdl").orElseThrow()) {
            assertThat(new String(text.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("Micronaut Core");
            assertThat(new String(encoded.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("native-image");
        }
    }

    @Test
    void loadsVersionInformationFromPackagedResource() {
        assertThat(VersionUtils.getMicronautVersion()).isNotBlank();
        assertThat(VersionUtils.isAtLeastMicronautVersion("1.0.0")).isTrue();
    }

    @Test
    void convertsScalarAndParameterizedValues() {
        ConversionService conversionService = new DefaultMutableConversionService();
        UUID identifier = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Argument<Set<Integer>> integerSet = Argument.setOf(Integer.class);
        Argument<Map<Integer, URI>> endpointMap = Argument.mapOf(Integer.class, URI.class);

        assertThat(conversionService.convertRequired("41", Integer.class)).isEqualTo(41);
        assertThat(conversionService.convertRequired(identifier.toString(), UUID.class))
                .isEqualTo(identifier);
        assertThat(conversionService.convertRequired("3,1,3,2", integerSet))
                .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(
                        conversionService.convertRequired(
                                Map.of("7", "https://example.test/items"), endpointMap))
                .containsEntry(7, URI.create("https://example.test/items"));
        assertThat(conversionService.convertRequired("5,8,13", int[].class))
                .containsExactly(5, 8, 13);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void convertsToConcreteCollectionThroughPublicCollectionApi() {
        Class<? extends Iterable<Integer>> treeSetType = (Class) TreeSet.class;
        Iterable<Integer> converted =
                CollectionUtils.convertCollection(treeSetType, List.of(8, 3, 5, 3))
                        .orElseThrow();

        assertThat(converted).isInstanceOf(TreeSet.class).containsExactly(3, 5, 8);
    }

    @Test
    void buildsAndReadsTypedAnnotationValues() {
        AnnotationValue<Retention> annotationValue =
                AnnotationValue.builder(Retention.class)
                        .member("value", RetentionPolicy.RUNTIME)
                        .member("types", String.class, URI.class)
                        .member("retries", 3)
                        .build();

        assertThat(annotationValue.enumValue("value", RetentionPolicy.class))
                .contains(RetentionPolicy.RUNTIME);
        assertThat(annotationValue.classValues("types"))
                .containsExactly(String.class, URI.class);
        assertThat(annotationValue.intValue("retries")).hasValue(3);
        assertThat(annotationValue.getMemberNames())
                .containsExactlyInAnyOrder("value", "types", "retries");
    }

    @Test
    void createsAndUpdatesBeanThroughCompileTimeIntrospection() {
        BeanIntrospection<ServerSettings> introspection =
                BeanIntrospection.getIntrospection(ServerSettings.class);
        ServerSettings settings = introspection.instantiate();
        BeanProperty<ServerSettings, String> host =
                introspection.getRequiredProperty("host", String.class);
        BeanProperty<ServerSettings, Integer> port =
                introspection.getRequiredProperty("port", int.class);

        host.set(settings, "localhost");
        port.set(settings, 8080);

        assertThat(introspection.getBeanType()).isEqualTo(ServerSettings.class);
        assertThat(host.get(settings)).isEqualTo("localhost");
        assertThat(port.get(settings)).isEqualTo(8080);
        assertThat(settings.getHost()).isEqualTo("localhost");
        assertThat(settings.getPort()).isEqualTo(8080);
    }

    @Test
    void convertsAndMutatesMapBackedValues() {
        MutableConvertibleValuesMap<Object> values = new MutableConvertibleValuesMap<>();
        values.put("server.port", "8080");
        values.put("feature.enabled", "true");
        values.put("attempts", 4);

        assertThat(values.get("server.port", Integer.class)).contains(8080);
        assertThat(values.get("feature.enabled", Boolean.class)).contains(true);
        assertThat(values.subMap("server", Object.class)).containsEntry("port", "8080");
        assertThat(values.asProperties())
                .containsEntry("feature.enabled", "true")
                .containsEntry("attempts", "4");

        values.remove("feature.enabled");
        assertThat(values.contains("feature.enabled")).isFalse();
    }

    @Test
    void readsAndDuplicatesJdkBackedBuffers() {
        ReadBufferFactory factory = ReadBufferFactory.getJdkFactory();

        try (ReadBuffer buffer = factory.copyOf("micronaut", StandardCharsets.UTF_8);
                ReadBuffer duplicate = buffer.duplicate()) {
            assertThat(buffer.readable()).isEqualTo(9);
            assertThat(buffer.toString(StandardCharsets.UTF_8)).isEqualTo("micronaut");
            assertThat(duplicate.toArray())
                    .containsExactly("micronaut".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void parsesConnectionStringComponentsAndCanonicalPath() {
        ConnectionString connection =
                ConnectionString.parse(
                        "optional:consul://user:secret@host-a:8500,host-b/app/../config.yml"
                                + "?watch=true&dc=west");

        assertThat(connection.isOptional()).isTrue();
        assertThat(connection.getProtocol()).isEqualTo("consul");
        assertThat(connection.getUsername()).contains("user");
        assertThat(connection.getPassword()).contains("secret");
        assertThat(connection.getHosts())
                .containsExactly(
                        new ConnectionString.HostPort("host-a", 8500),
                        new ConnectionString.HostPort("host-b", null));
        assertThat(connection.getCanonicalPath()).isEqualTo("config.yml");
        assertThat(connection.getExtension()).contains("yml");
        assertThat(connection.getOptions())
                .containsEntry("dc", "west")
                .containsEntry("watch", "true");
    }

    @Test
    void transformsNamesAndSortsOrderedComponents() {
        List<OrderedComponent> components =
                List.of(
                        new OrderedComponent("last", 20),
                        new OrderedComponent("first", -10),
                        new OrderedComponent("middle", 0));

        assertThat(NameUtils.hyphenate("ServerConnectionPool"))
                .isEqualTo("server-connection-pool");
        assertThat(NameUtils.environmentName("micronaut.serverPort"))
                .isEqualTo("MICRONAUT_SERVER_PORT");
        assertThat(OrderUtil.sortOrderedCollection(components))
                .extracting(OrderedComponent::name)
                .containsExactly("first", "middle", "last");
    }

    @Test
    void propagatesAndRestoresContextElements() {
        RequestElement request = new RequestElement("request-42");
        PropagatedContext context = PropagatedContext.empty().plus(request);

        assertThat(context.isEmpty()).isFalse();
        assertThat(PropagatedContext.exists()).isFalse();
        assertThat(
                        context.propagate(
                                () -> {
                                    assertThat(PropagatedContext.exists()).isTrue();
                                    assertThat(context.isBound()).isTrue();
                                    return PropagatedContext.get().get(RequestElement.class).value();
                                }))
                .isEqualTo("request-42");
        assertThat(PropagatedContext.exists()).isFalse();
    }

    @Introspected
    public static final class ServerSettings {
        private String host;
        private int port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    private record OrderedComponent(String name, int order) implements Ordered {
        @Override
        public int getOrder() {
            return order;
        }
    }

    private record RequestElement(String value) implements PropagatedContextElement {}
}
