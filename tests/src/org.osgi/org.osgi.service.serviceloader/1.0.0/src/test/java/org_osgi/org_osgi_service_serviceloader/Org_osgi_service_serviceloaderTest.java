/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_serviceloader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.serviceloader.ServiceLoaderNamespace;

public class Org_osgi_service_serviceloaderTest {
    private static final String SERVICE_TYPE = "org.example.spi.ExampleService";
    private static final String FIRST_PROVIDER = "org.example.impl.FirstProvider";
    private static final String SECOND_PROVIDER = "org.example.impl.SecondProvider";

    @Test
    void serviceLoaderNamespaceNamesCapabilityAndServiceTypeAttribute() {
        Capability capability = serviceLoaderCapability(Map.of(), Map.of("format", "java.util.ServiceLoader"));

        assertThat(capability.getNamespace()).isEqualTo("osgi.serviceloader");
        assertThat(capability.getNamespace()).isEqualTo(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE);
        assertThat(capability.getAttributes())
                .containsEntry(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE, SERVICE_TYPE)
                .containsEntry("format", "java.util.ServiceLoader");
    }

    @Test
    void capabilityWithoutRegisterDirectiveAdvertisesAllProvidersForTheServiceType() {
        Capability capability = serviceLoaderCapability(Map.of(), Map.of());

        assertThat(capability.getDirectives())
                .doesNotContainKey(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE);
        assertThat(capability.getAttributes())
                .containsOnly(Map.entry(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE, SERVICE_TYPE));
    }

    @Test
    void registerDirectiveCanRestrictTheProviderImplementationClasses() {
        String registeredProviders = FIRST_PROVIDER + "," + SECOND_PROVIDER;
        Capability capability = serviceLoaderCapability(
                Map.of(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE, registeredProviders),
                Map.of()
        );

        assertThat(capability.getDirectives())
                .containsOnly(Map.entry(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE, registeredProviders));
    }

    @Test
    void emptyRegisterDirectiveRepresentsNoServiceRegistrationForTheCapability() {
        Capability capability = serviceLoaderCapability(
                Map.of(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE, ""),
                Map.of()
        );

        assertThat(capability.getDirectives()).containsEntry(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE, "");
    }

    @Test
    void serviceLoaderRequirementUsesNamespaceAttributeInStandardRequirementDirectives() {
        Requirement requirement = serviceLoaderRequirement(
                Namespace.RESOLUTION_OPTIONAL,
                Namespace.CARDINALITY_MULTIPLE
        );

        assertThat(requirement.getNamespace()).isEqualTo(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE);
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.serviceloader=" + SERVICE_TYPE + ")")
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL)
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
        assertThat(requirement.getAttributes()).isEmpty();
    }

    @Test
    void serviceLoaderNamespaceWorksAsResourceNamespaceSelector() {
        InMemoryResource resource = new InMemoryResource();
        Capability serviceLoaderCapability = serviceLoaderCapability(resource, Map.of(), Map.of());
        Capability identityCapability = new SimpleCapability(
                "osgi.identity",
                Map.of(),
                Map.of("osgi.identity", "example.bundle"),
                resource
        );
        Requirement serviceLoaderRequirement = serviceLoaderRequirement(
                resource,
                Namespace.RESOLUTION_MANDATORY,
                Namespace.CARDINALITY_SINGLE
        );
        Requirement executionEnvironmentRequirement = new SimpleRequirement(
                "osgi.ee",
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.ee=JavaSE)"),
                Map.of(),
                resource
        );

        resource.addCapability(identityCapability);
        resource.addRequirement(executionEnvironmentRequirement);

        assertThat(resource.getCapabilities(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE))
                .containsExactly(serviceLoaderCapability);
        assertThat(resource.getRequirements(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE))
                .containsExactly(serviceLoaderRequirement);
        assertThat(resource.getCapabilities(null)).containsExactly(serviceLoaderCapability, identityCapability);
        assertThat(resource.getRequirements(null))
                .containsExactly(serviceLoaderRequirement, executionEnvironmentRequirement);
    }

    @Test
    void serviceLoaderSpecificConstantsDoNotCollideWithGenericNamespaceKeys() {
        assertThat(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE)
                .isNotEqualTo(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE)
                .isNotEqualTo(Namespace.REQUIREMENT_FILTER_DIRECTIVE)
                .isNotEqualTo(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE)
                .isNotEqualTo(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE)
                .isNotEqualTo(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE)
                .isNotEqualTo(Namespace.CAPABILITY_USES_DIRECTIVE);
        assertThat(ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE)
                .isEqualTo("register")
                .isNotEqualTo(Namespace.REQUIREMENT_FILTER_DIRECTIVE)
                .isNotEqualTo(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE);
    }

    private static Capability serviceLoaderCapability(
            Map<String, String> directives,
            Map<String, Object> additionalAttributes
    ) {
        return serviceLoaderCapability(new InMemoryResource(), directives, additionalAttributes);
    }

    private static Capability serviceLoaderCapability(
            InMemoryResource resource,
            Map<String, String> directives,
            Map<String, Object> additionalAttributes
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(ServiceLoaderNamespace.SERVICELOADER_NAMESPACE, SERVICE_TYPE);
        attributes.putAll(additionalAttributes);
        Capability capability = new SimpleCapability(
                ServiceLoaderNamespace.SERVICELOADER_NAMESPACE,
                directives,
                attributes,
                resource
        );
        resource.addCapability(capability);
        return capability;
    }

    private static Requirement serviceLoaderRequirement(String resolution, String cardinality) {
        return serviceLoaderRequirement(new InMemoryResource(), resolution, cardinality);
    }

    private static Requirement serviceLoaderRequirement(
            InMemoryResource resource,
            String resolution,
            String cardinality
    ) {
        Map<String, String> directives = new LinkedHashMap<>();
        String filter = "(" + ServiceLoaderNamespace.SERVICELOADER_NAMESPACE + "=" + SERVICE_TYPE + ")";
        directives.put(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filter);
        directives.put(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, resolution);
        directives.put(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, cardinality);
        Requirement requirement = new SimpleRequirement(
                ServiceLoaderNamespace.SERVICELOADER_NAMESPACE,
                directives,
                Map.of(),
                resource
        );
        resource.addRequirement(requirement);
        return requirement;
    }

    private static final class InMemoryResource implements Resource {
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

        void addCapability(Capability capability) {
            capabilities.add(capability);
        }

        void addRequirement(Requirement requirement) {
            requirements.add(requirement);
        }

        @Override
        public List<Capability> getCapabilities(String namespace) {
            return matchingNamespace(capabilities, namespace);
        }

        @Override
        public List<Requirement> getRequirements(String namespace) {
            return matchingNamespace(requirements, namespace);
        }
    }

    private static final class SimpleCapability implements Capability {
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;
        private final Resource resource;

        SimpleCapability(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes,
                Resource resource
        ) {
            this.namespace = namespace;
            this.directives = Map.copyOf(directives);
            this.attributes = Map.copyOf(attributes);
            this.resource = resource;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public Map<String, String> getDirectives() {
            return directives;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Resource getResource() {
            return resource;
        }
    }

    private static final class SimpleRequirement implements Requirement {
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;
        private final Resource resource;

        SimpleRequirement(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes,
                Resource resource
        ) {
            this.namespace = namespace;
            this.directives = Map.copyOf(directives);
            this.attributes = Map.copyOf(attributes);
            this.resource = resource;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public Map<String, String> getDirectives() {
            return directives;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Resource getResource() {
            return resource;
        }
    }

    private static <T> List<T> matchingNamespace(List<T> namespaceObjects, String namespace) {
        if (namespace == null) {
            return List.copyOf(namespaceObjects);
        }
        return namespaceObjects.stream()
                .filter(namespaceObject -> namespace.equals(namespaceOf(namespaceObject)))
                .collect(Collectors.toUnmodifiableList());
    }

    private static String namespaceOf(Object namespaceObject) {
        if (namespaceObject instanceof Capability) {
            return ((Capability) namespaceObject).getNamespace();
        }
        return ((Requirement) namespaceObject).getNamespace();
    }
}
