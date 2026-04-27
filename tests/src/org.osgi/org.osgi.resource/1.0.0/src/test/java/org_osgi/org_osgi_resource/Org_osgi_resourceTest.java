/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.resource.dto.CapabilityDTO;
import org.osgi.resource.dto.CapabilityRefDTO;
import org.osgi.resource.dto.RequirementDTO;
import org.osgi.resource.dto.RequirementRefDTO;
import org.osgi.resource.dto.ResourceDTO;
import org.osgi.resource.dto.WireDTO;
import org.osgi.resource.dto.WiringDTO;

public class Org_osgi_resourceTest {
    private static final String IDENTITY_NAMESPACE = "osgi.identity";
    private static final String PACKAGE_NAMESPACE = "osgi.wiring.package";
    private static final String BUNDLE_NAMESPACE = "osgi.wiring.bundle";
    private static final String HOST_NAMESPACE = "osgi.wiring.host";

    @Test
    void namespaceConstantsDefineCommonDirectiveNamesAndDefaultValues() {
        assertThat(Namespace.CAPABILITY_USES_DIRECTIVE).isEqualTo("uses");
        assertThat(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE).isEqualTo("effective");
        assertThat(Namespace.REQUIREMENT_FILTER_DIRECTIVE).isEqualTo("filter");
        assertThat(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE).isEqualTo("resolution");
        assertThat(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE).isEqualTo("effective");
        assertThat(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE).isEqualTo("cardinality");
        assertThat(Namespace.RESOLUTION_MANDATORY).isEqualTo("mandatory");
        assertThat(Namespace.RESOLUTION_OPTIONAL).isEqualTo("optional");
        assertThat(Namespace.EFFECTIVE_RESOLVE).isEqualTo("resolve");
        assertThat(Namespace.EFFECTIVE_ACTIVE).isEqualTo("active");
        assertThat(Namespace.CARDINALITY_SINGLE).isEqualTo("single");
        assertThat(Namespace.CARDINALITY_MULTIPLE).isEqualTo("multiple");
        assertThat(new CustomNamespace()).isInstanceOf(Namespace.class);
    }

    @Test
    void resourceFiltersCapabilitiesAndRequirementsByNamespaceInDeclarationOrder() {
        SimpleResource resource = new SimpleResource("repository:sample");
        SimpleCapability identityCapability = resource.addCapability(
                IDENTITY_NAMESPACE,
                Map.of(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE),
                Map.of(IDENTITY_NAMESPACE, "sample.bundle", "version", "1.2.3"));
        SimpleCapability apiCapability = resource.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.spi,com.acme.model"),
                Map.of(PACKAGE_NAMESPACE, "com.acme.api"));
        SimpleCapability internalCapability = resource.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE),
                Map.of(PACKAGE_NAMESPACE, "com.acme.internal"));
        SimpleRequirement packageRequirement = resource.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.spi)"),
                Map.of("declaredBy", "Import-Package"));
        SimpleRequirement bundleRequirement = resource.addRequirement(
                BUNDLE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.bundle=provider.bundle)"),
                Map.of("declaredBy", "Require-Bundle"));

        assertThat(resource.getCapabilities(null))
                .containsExactly(identityCapability, apiCapability, internalCapability);
        assertThat(resource.getCapabilities(PACKAGE_NAMESPACE)).containsExactly(apiCapability, internalCapability);
        assertThat(resource.getCapabilities(IDENTITY_NAMESPACE)).containsExactly(identityCapability);
        assertThat(resource.getCapabilities(HOST_NAMESPACE)).isEmpty();
        assertThat(resource.getRequirements(null)).containsExactly(packageRequirement, bundleRequirement);
        assertThat(resource.getRequirements(PACKAGE_NAMESPACE)).containsExactly(packageRequirement);
        assertThat(resource.getRequirements(HOST_NAMESPACE)).isEmpty();
        assertThatThrownBy(() -> resource.getCapabilities(null).add(identityCapability))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> resource.getRequirements(null).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void capabilityExposesNamespaceDirectivesAttributesAndDeclaringResource() {
        SimpleResource resource = new SimpleResource("repository:provider");
        Capability capability = resource.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(
                        Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.model",
                        Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE),
                Map.of(PACKAGE_NAMESPACE, "com.acme.api", "version", "2.0.0", "mandatory", List.of("version")));

        assertThat(capability.getNamespace()).isEqualTo(PACKAGE_NAMESPACE);
        assertThat(capability.getResource()).isSameAs(resource);
        assertThat(capability.getDirectives())
                .containsEntry(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.model")
                .containsEntry(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
        assertThat(capability.getAttributes())
                .containsEntry(PACKAGE_NAMESPACE, "com.acme.api")
                .containsEntry("version", "2.0.0")
                .containsEntry("mandatory", List.of("version"));
        assertThatThrownBy(() -> capability.getDirectives().put("extra", "directive"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> capability.getAttributes().put("extra", "attribute"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requirementExposesFilterResolutionEffectiveCardinalityAndCustomAttributes() {
        SimpleResource resource = new SimpleResource("repository:consumer");
        Requirement requirement = resource.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.wiring.package=com.acme.api)(version>=2.0.0))",
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE,
                        Namespace.RESOLUTION_OPTIONAL,
                        Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE,
                        Namespace.EFFECTIVE_ACTIVE,
                        Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE,
                        Namespace.CARDINALITY_MULTIPLE),
                Map.of("source", "Require-Capability", "line", 42L));

        assertThat(requirement.getNamespace()).isEqualTo(PACKAGE_NAMESPACE);
        assertThat(requirement.getResource()).isSameAs(resource);
        assertThat(requirement.getDirectives())
                .containsEntry(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.wiring.package=com.acme.api)(version>=2.0.0))")
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL)
                .containsEntry(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE)
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
        assertThat(requirement.getAttributes())
                .containsEntry("source", "Require-Capability")
                .containsEntry("line", 42L);
        assertThat(resolutionDirective(requirement)).isEqualTo(Namespace.RESOLUTION_OPTIONAL);
        assertThat(effectiveRequirementDirective(requirement)).isEqualTo(Namespace.EFFECTIVE_ACTIVE);
        assertThat(cardinalityDirective(requirement)).isEqualTo(Namespace.CARDINALITY_MULTIPLE);
    }

    @Test
    void missingRequirementDirectivesUseMandatoryResolveAndSingleDefaults() {
        SimpleResource resource = new SimpleResource("repository:consumer");
        Requirement requirement = resource.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.api)"),
                Map.of());

        assertThat(resolutionDirective(requirement)).isEqualTo(Namespace.RESOLUTION_MANDATORY);
        assertThat(effectiveRequirementDirective(requirement)).isEqualTo(Namespace.EFFECTIVE_RESOLVE);
        assertThat(cardinalityDirective(requirement)).isEqualTo(Namespace.CARDINALITY_SINGLE);
        assertThat(requirement.getAttributes()).isEmpty();
    }

    @Test
    void capabilitiesRequirementsResourcesAndWiresDefineValueEquality() {
        SimpleResource provider = new SimpleResource("repository:provider");
        SimpleResource requirer = new SimpleResource("repository:consumer");
        SimpleCapability capability = provider.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.model"),
                Map.of(PACKAGE_NAMESPACE, "com.acme.api"));
        SimpleRequirement requirement = requirer.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.api)"),
                Map.of("source", "Import-Package"));
        SimpleWire wire = new SimpleWire(capability, requirement, provider, requirer);

        assertThat(capability).isEqualTo(new SimpleCapability(
                provider,
                PACKAGE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.model"),
                Map.of(PACKAGE_NAMESPACE, "com.acme.api")));
        assertThat(capability.hashCode()).isEqualTo(new SimpleCapability(
                provider,
                PACKAGE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.model"),
                Map.of(PACKAGE_NAMESPACE, "com.acme.api")).hashCode());
        assertThat(requirement).isEqualTo(new SimpleRequirement(
                requirer,
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.api)"),
                Map.of("source", "Import-Package")));
        assertThat(wire).isEqualTo(new SimpleWire(capability, requirement, provider, requirer));
        assertThat(new SimpleResource("repository:provider")).isEqualTo(provider);
        assertThat(capability).isNotEqualTo(new SimpleCapability(provider, HOST_NAMESPACE, Map.of(), Map.of()));
        assertThat(requirement).isNotEqualTo(new SimpleRequirement(requirer, HOST_NAMESPACE, Map.of(), Map.of()));
        assertThat(wire).isNotEqualTo(new SimpleWire(capability, requirement, requirer, provider));
    }

    @Test
    void synthesizedRequirementCanBeWiredWithoutDeclaringResource() {
        SimpleResource provider = new SimpleResource("repository:provider");
        SimpleResource consumer = new SimpleResource("repository:consumer");
        Capability capability = provider.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(),
                Map.of(PACKAGE_NAMESPACE, "com.acme.synthesized.api"));
        Requirement synthesizedRequirement = new SimpleRequirement(
                null,
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.synthesized.api)"),
                Map.of("origin", "resolver"));
        Wire wire = new SimpleWire(capability, synthesizedRequirement, provider, consumer);

        assertThat(synthesizedRequirement.getNamespace()).isEqualTo(PACKAGE_NAMESPACE);
        assertThat(synthesizedRequirement.getResource()).isNull();
        assertThat(synthesizedRequirement.getDirectives())
                .containsEntry(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(osgi.wiring.package=com.acme.synthesized.api)");
        assertThat(synthesizedRequirement.getAttributes()).containsEntry("origin", "resolver");
        assertThat(wire.getCapability()).isSameAs(capability);
        assertThat(wire.getRequirement()).isSameAs(synthesizedRequirement);
        assertThat(wire.getProvider()).isSameAs(provider);
        assertThat(wire.getRequirer()).isSameAs(consumer);
    }

    @Test
    void wireCanConnectCapabilitiesAndRequirementsThroughDifferentProviderAndRequirerResources() {
        SimpleResource host = new SimpleResource("repository:host");
        SimpleResource fragment = new SimpleResource("repository:fragment");
        SimpleResource consumer = new SimpleResource("repository:consumer");
        Capability fragmentCapability = fragment.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(),
                Map.of(PACKAGE_NAMESPACE, "com.acme.fragment.api"));
        Requirement consumerRequirement = consumer.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.fragment.api)"),
                Map.of());
        Wire wire = new SimpleWire(fragmentCapability, consumerRequirement, host, consumer);

        assertThat(wire.getCapability()).isSameAs(fragmentCapability);
        assertThat(wire.getRequirement()).isSameAs(consumerRequirement);
        assertThat(wire.getProvider()).isSameAs(host);
        assertThat(wire.getCapability().getResource()).isSameAs(fragment);
        assertThat(wire.getRequirer()).isSameAs(consumer);
    }

    @Test
    void wiringReportsResolveTimeCapabilitiesRequirementsAndNamespaceFilteredWires() {
        SimpleResource provider = new SimpleResource("repository:provider");
        SimpleResource consumer = new SimpleResource("repository:consumer");
        SimpleCapability identityCapability = provider.addCapability(
                IDENTITY_NAMESPACE,
                Map.of(),
                Map.of(IDENTITY_NAMESPACE, "provider.bundle"));
        SimpleCapability packageCapability = provider.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(),
                Map.of(PACKAGE_NAMESPACE, "com.acme.api"));
        provider.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE),
                Map.of(PACKAGE_NAMESPACE, "com.acme.runtime"));
        SimpleRequirement packageRequirement = consumer.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.api)"),
                Map.of());
        consumer.addRequirement(
                HOST_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE),
                Map.of("host", "optional"));
        SimpleWire packageWire = new SimpleWire(packageCapability, packageRequirement, provider, consumer);
        SimpleWiring providerWiring = new SimpleWiring(provider);
        SimpleWiring consumerWiring = new SimpleWiring(consumer);
        providerWiring.addProvidedWire(packageWire);
        consumerWiring.addRequiredWire(packageWire);

        assertThat(providerWiring.getResource()).isSameAs(provider);
        assertThat(providerWiring.getResourceCapabilities(null)).containsExactly(identityCapability, packageCapability);
        assertThat(providerWiring.getResourceCapabilities(PACKAGE_NAMESPACE)).containsExactly(packageCapability);
        assertThat(consumerWiring.getResourceRequirements(null)).containsExactly(packageRequirement);
        assertThat(consumerWiring.getResourceRequirements(PACKAGE_NAMESPACE)).containsExactly(packageRequirement);
        assertThat(providerWiring.getProvidedResourceWires(PACKAGE_NAMESPACE)).containsExactly(packageWire);
        assertThat(providerWiring.getProvidedResourceWires(IDENTITY_NAMESPACE)).isEmpty();
        assertThat(consumerWiring.getRequiredResourceWires(PACKAGE_NAMESPACE)).containsExactly(packageWire);
        assertThat(consumerWiring.getRequiredResourceWires(IDENTITY_NAMESPACE)).isEmpty();
        assertThatThrownBy(() -> providerWiring.getProvidedResourceWires(null).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void wiringReturnedListsAreSnapshotsIndependentOfLaterResolutionChanges() {
        SimpleResource provider = new SimpleResource("repository:provider");
        SimpleResource consumer = new SimpleResource("repository:consumer");
        SimpleCapability firstCapability = provider.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(),
                Map.of(PACKAGE_NAMESPACE, "com.acme.first"));
        SimpleRequirement firstRequirement = consumer.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.first)"),
                Map.of());
        SimpleWire firstWire = new SimpleWire(firstCapability, firstRequirement, provider, consumer);
        SimpleWiring providerWiring = new SimpleWiring(provider);
        providerWiring.addProvidedWire(firstWire);

        List<Capability> capabilitySnapshot = providerWiring.getResourceCapabilities(PACKAGE_NAMESPACE);
        List<Wire> providedWireSnapshot = providerWiring.getProvidedResourceWires(PACKAGE_NAMESPACE);

        SimpleCapability secondCapability = provider.addCapability(
                PACKAGE_NAMESPACE,
                Map.of(),
                Map.of(PACKAGE_NAMESPACE, "com.acme.second"));
        SimpleRequirement secondRequirement = consumer.addRequirement(
                PACKAGE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme.second)"),
                Map.of());
        SimpleWire secondWire = new SimpleWire(secondCapability, secondRequirement, provider, consumer);
        providerWiring.addProvidedWire(secondWire);

        assertThat(capabilitySnapshot).containsExactly(firstCapability);
        assertThat(providedWireSnapshot).containsExactly(firstWire);
        assertThat(providerWiring.getResourceCapabilities(PACKAGE_NAMESPACE))
                .containsExactly(firstCapability, secondCapability);
        assertThat(providerWiring.getProvidedResourceWires(PACKAGE_NAMESPACE)).containsExactly(firstWire, secondWire);
    }

    @Test
    void resourceDtoGraphRepresentsCapabilitiesAndRequirementsWithPublicFields() {
        CapabilityDTO capability = new CapabilityDTO();
        capability.id = 11;
        capability.resource = 1;
        capability.namespace = PACKAGE_NAMESPACE;
        capability.directives = Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.model");
        capability.attributes = Map.of(
                PACKAGE_NAMESPACE,
                "com.acme.api",
                "version",
                "3.1.4",
                "mandatory",
                new String[] {"version"});

        RequirementDTO requirement = new RequirementDTO();
        requirement.id = 17;
        requirement.resource = 1;
        requirement.namespace = PACKAGE_NAMESPACE;
        requirement.directives = Map.of(
                Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                "(&(osgi.wiring.package=com.acme.api)(version>=3.0.0))");
        requirement.attributes = Map.of("optional", Boolean.TRUE, "ranking", 100L);

        ResourceDTO resource = new ResourceDTO();
        resource.id = 1;
        resource.capabilities = List.of(capability);
        resource.requirements = List.of(requirement);

        assertThat(resource.id).isEqualTo(1);
        assertThat(resource.capabilities).containsExactly(capability);
        assertThat(resource.requirements).containsExactly(requirement);
        assertThat(resource.capabilities.get(0).namespace).isEqualTo(PACKAGE_NAMESPACE);
        assertThat(resource.capabilities.get(0).attributes.get("mandatory")).isEqualTo(new String[] {"version"});
        assertThat(resource.requirements.get(0).directives)
                .containsEntry(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.wiring.package=com.acme.api)(version>=3.0.0))");
    }

    @Test
    void wiringDtoGraphUsesReferencesAndWireDtosToDescribeResolvedConnections() {
        CapabilityRefDTO capabilityReference = new CapabilityRefDTO();
        capabilityReference.capability = 11;
        capabilityReference.resource = 1;

        RequirementRefDTO requirementReference = new RequirementRefDTO();
        requirementReference.requirement = 17;
        requirementReference.resource = 2;

        WireDTO wire = new WireDTO();
        wire.capability = capabilityReference;
        wire.requirement = requirementReference;
        wire.provider = 1;
        wire.requirer = 2;

        WiringDTO providerWiring = new WiringDTO();
        providerWiring.id = 101;
        providerWiring.resource = 1;
        providerWiring.capabilities = List.of(capabilityReference);
        providerWiring.requirements = List.of();
        providerWiring.providedWires = List.of(wire);
        providerWiring.requiredWires = List.of();

        WiringDTO requirerWiring = new WiringDTO();
        requirerWiring.id = 102;
        requirerWiring.resource = 2;
        requirerWiring.capabilities = List.of();
        requirerWiring.requirements = List.of(requirementReference);
        requirerWiring.providedWires = List.of();
        requirerWiring.requiredWires = List.of(wire);

        assertThat(providerWiring.capabilities).containsExactly(capabilityReference);
        assertThat(providerWiring.providedWires.get(0).capability.capability).isEqualTo(11);
        assertThat(providerWiring.providedWires.get(0).requirement.requirement).isEqualTo(17);
        assertThat(requirerWiring.requirements).containsExactly(requirementReference);
        assertThat(requirerWiring.requiredWires).containsExactly(wire);
        assertThat(wire.provider).isEqualTo(providerWiring.resource);
        assertThat(wire.requirer).isEqualTo(requirerWiring.resource);
    }

    private static String resolutionDirective(Requirement requirement) {
        return requirement.getDirectives()
                .getOrDefault(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_MANDATORY);
    }

    private static String effectiveCapabilityDirective(Capability capability) {
        return capability.getDirectives()
                .getOrDefault(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
    }

    private static String effectiveRequirementDirective(Requirement requirement) {
        return requirement.getDirectives()
                .getOrDefault(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
    }

    private static String cardinalityDirective(Requirement requirement) {
        return requirement.getDirectives()
                .getOrDefault(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_SINGLE);
    }

    public static final class CustomNamespace extends Namespace {
    }

    private static final class SimpleResource implements Resource {
        private final String location;
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

        private SimpleResource(String location) {
            this.location = location;
        }

        private SimpleCapability addCapability(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            SimpleCapability capability = new SimpleCapability(this, namespace, directives, attributes);
            capabilities.add(capability);
            return capability;
        }

        private SimpleRequirement addRequirement(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            SimpleRequirement requirement = new SimpleRequirement(this, namespace, directives, attributes);
            requirements.add(requirement);
            return requirement;
        }

        @Override
        public List<Capability> getCapabilities(String namespace) {
            return capabilities.stream()
                    .filter(capability -> namespace == null || Objects.equals(namespace, capability.getNamespace()))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public List<Requirement> getRequirements(String namespace) {
            return requirements.stream()
                    .filter(requirement -> namespace == null || Objects.equals(namespace, requirement.getNamespace()))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SimpleResource)) {
                return false;
            }
            SimpleResource that = (SimpleResource) object;
            return Objects.equals(location, that.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location);
        }
    }

    private static final class SimpleCapability implements Capability {
        private final Resource resource;
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;

        private SimpleCapability(
                Resource resource,
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            this.resource = resource;
            this.namespace = namespace;
            this.directives = Map.copyOf(directives);
            this.attributes = Map.copyOf(attributes);
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

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Capability)) {
                return false;
            }
            Capability that = (Capability) object;
            return Objects.equals(namespace, that.getNamespace())
                    && Objects.equals(directives, that.getDirectives())
                    && Objects.equals(attributes, that.getAttributes())
                    && Objects.equals(resource, that.getResource());
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, directives, attributes, resource);
        }
    }

    private static final class SimpleRequirement implements Requirement {
        private final Resource resource;
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;

        private SimpleRequirement(
                Resource resource,
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            this.resource = resource;
            this.namespace = namespace;
            this.directives = Map.copyOf(directives);
            this.attributes = Map.copyOf(attributes);
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

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Requirement)) {
                return false;
            }
            Requirement that = (Requirement) object;
            return Objects.equals(namespace, that.getNamespace())
                    && Objects.equals(directives, that.getDirectives())
                    && Objects.equals(attributes, that.getAttributes())
                    && Objects.equals(resource, that.getResource());
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, directives, attributes, resource);
        }
    }

    private static final class SimpleWire implements Wire {
        private final Capability capability;
        private final Requirement requirement;
        private final Resource provider;
        private final Resource requirer;

        private SimpleWire(Capability capability, Requirement requirement, Resource provider, Resource requirer) {
            this.capability = capability;
            this.requirement = requirement;
            this.provider = provider;
            this.requirer = requirer;
        }

        @Override
        public Capability getCapability() {
            return capability;
        }

        @Override
        public Requirement getRequirement() {
            return requirement;
        }

        @Override
        public Resource getProvider() {
            return provider;
        }

        @Override
        public Resource getRequirer() {
            return requirer;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Wire)) {
                return false;
            }
            Wire that = (Wire) object;
            return Objects.equals(capability, that.getCapability())
                    && Objects.equals(requirement, that.getRequirement())
                    && Objects.equals(provider, that.getProvider())
                    && Objects.equals(requirer, that.getRequirer());
        }

        @Override
        public int hashCode() {
            return Objects.hash(capability, requirement, provider, requirer);
        }
    }

    private static final class SimpleWiring implements Wiring {
        private final Resource resource;
        private final List<Wire> providedWires = new ArrayList<>();
        private final List<Wire> requiredWires = new ArrayList<>();

        private SimpleWiring(Resource resource) {
            this.resource = resource;
        }

        private void addProvidedWire(Wire wire) {
            providedWires.add(wire);
        }

        private void addRequiredWire(Wire wire) {
            requiredWires.add(wire);
        }

        @Override
        public List<Capability> getResourceCapabilities(String namespace) {
            return resource.getCapabilities(namespace).stream()
                    .filter(capability -> Namespace.EFFECTIVE_RESOLVE.equals(effectiveCapabilityDirective(capability)))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public List<Requirement> getResourceRequirements(String namespace) {
            return resource.getRequirements(namespace).stream()
                    .filter(requirement -> Namespace.EFFECTIVE_RESOLVE.equals(
                            effectiveRequirementDirective(requirement)))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public List<Wire> getProvidedResourceWires(String namespace) {
            return providedWires.stream()
                    .filter(wire -> namespace == null || Objects.equals(namespace, wire.getCapability().getNamespace()))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public List<Wire> getRequiredResourceWires(String namespace) {
            return requiredWires.stream()
                    .filter(wire -> namespace == null
                            || Objects.equals(namespace, wire.getRequirement().getNamespace()))
                    .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public Resource getResource() {
            return resource;
        }
    }
}
