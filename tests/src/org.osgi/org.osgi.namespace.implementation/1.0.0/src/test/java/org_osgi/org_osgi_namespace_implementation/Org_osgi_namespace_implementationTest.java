/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_namespace_implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.namespace.implementation.ImplementationNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class Org_osgi_namespace_implementationTest {
    private static final String HTTP_WHITEBOARD_IMPLEMENTATION = "osgi.http";
    private static final String JAX_RS_IMPLEMENTATION = "osgi.jaxrs";
    private static final String TRANSACTION_IMPLEMENTATION = "osgi.transaction";
    private static final String VENDOR_ATTRIBUTE = "vendor";
    private static final String PROFILE_ATTRIBUTE = "profile";
    private static final String DISTRIBUTION_ATTRIBUTE = "distribution";

    @Test
    void constantsExposeImplementationNamespaceContract() {
        assertThat(ImplementationNamespace.IMPLEMENTATION_NAMESPACE).isEqualTo("osgi.implementation");
        assertThat(ImplementationNamespace.IMPLEMENTATION_NAMESPACE).startsWith("osgi.").doesNotContain(" ");
        assertThat(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE).isEqualTo("version");
        assertThat(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE)
                .isNotEqualTo(ImplementationNamespace.IMPLEMENTATION_NAMESPACE);
    }

    @Test
    void implementationCapabilityUsesNamespaceAsNameAttributeAndVersionAttribute() {
        SyntheticResource resource = new SyntheticResource();
        Version implementationVersion = Version.parseVersion("1.1.0");
        Capability capability = resource.addCapability(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "org.osgi.service.http.whiteboard,org.osgi.service.servlet"),
                Map.of(
                        ImplementationNamespace.IMPLEMENTATION_NAMESPACE, HTTP_WHITEBOARD_IMPLEMENTATION,
                        ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE, implementationVersion,
                        VENDOR_ATTRIBUTE, "felix"));

        assertThat(capability.getNamespace()).isEqualTo(ImplementationNamespace.IMPLEMENTATION_NAMESPACE);
        assertThat(capability.getResource()).isSameAs(resource);
        assertThat(capability.getDirectives())
                .containsEntry(Namespace.CAPABILITY_USES_DIRECTIVE,
                        "org.osgi.service.http.whiteboard,org.osgi.service.servlet");
        assertThat(capability.getAttributes())
                .containsEntry(ImplementationNamespace.IMPLEMENTATION_NAMESPACE, HTTP_WHITEBOARD_IMPLEMENTATION)
                .containsEntry(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE, implementationVersion)
                .containsEntry(VENDOR_ATTRIBUTE, "felix");
        assertThat(capabilityVersion(capability)).isEqualTo(new Version(1, 1, 0));
        assertThat(usedPackages(capability))
                .containsExactly("org.osgi.service.http.whiteboard", "org.osgi.service.servlet");
        assertThatThrownBy(() -> capability.getAttributes().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void implementationRequirementUsesNamespaceAndVersionRangeFilter() {
        SyntheticResource resource = new SyntheticResource();
        Capability minimumCompatible = resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", Map.of());
        Capability preferredCompatible = resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.2.0", Map.of());
        resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "2.0.0", Map.of());
        resource.addImplementationCapability(JAX_RS_IMPLEMENTATION, "1.1.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        implementationFilter(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", "2.0.0"),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE,
                        Namespace.RESOLUTION_MANDATORY),
                Map.of("consumer", "web.application"));

        assertThat(requirement.getNamespace()).isEqualTo(ImplementationNamespace.IMPLEMENTATION_NAMESPACE);
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.implementation=osgi.http)(version>=1.1.0)(!(version>=2.0.0)))")
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_MANDATORY);
        assertThat(requirement.getAttributes()).containsEntry("consumer", "web.application");
        assertThat(implementationCapabilitiesSatisfying(
                resource, requirement, HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", "2.0.0"))
                .containsExactly(minimumCompatible, preferredCompatible);
    }

    @Test
    void optionalActiveRequirementIsKeptSeparateFromResolveTimeRequirement() {
        SyntheticResource resource = new SyntheticResource();
        Requirement resolveRequirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        implementationFilter(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", "2.0.0")),
                Map.of());
        Requirement activeRequirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        implementationFilter(JAX_RS_IMPLEMENTATION, "1.0.0", "2.0.0"),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE,
                        Namespace.RESOLUTION_OPTIONAL,
                        Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE,
                        Namespace.EFFECTIVE_ACTIVE,
                        Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE,
                        Namespace.CARDINALITY_MULTIPLE),
                Map.of());

        assertThat(requirementEffectiveDirective(resolveRequirement)).isEqualTo(Namespace.EFFECTIVE_RESOLVE);
        assertThat(requirementResolutionDirective(resolveRequirement)).isEqualTo(Namespace.RESOLUTION_MANDATORY);
        assertThat(requirementCardinalityDirective(resolveRequirement)).isEqualTo(Namespace.CARDINALITY_SINGLE);
        assertThat(activeImplementationRequirements(resource)).containsExactly(activeRequirement);
        assertThat(activeRequirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL)
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
    }

    @Test
    void activeImplementationCapabilityIsDistinguishedFromResolveTimeCapabilities() {
        SyntheticResource resource = new SyntheticResource();
        Capability resolveCapability = resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", Map.of());
        Capability activeCapability = resource.addCapability(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE),
                Map.of(
                        ImplementationNamespace.IMPLEMENTATION_NAMESPACE, JAX_RS_IMPLEMENTATION,
                        ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.parseVersion("1.0.0")));

        assertThat(activeCapability.getDirectives())
                .containsEntry(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE);
        assertThat(resolveTimeImplementationCapabilities(resource)).containsExactly(resolveCapability);
        assertThat(activeImplementationCapabilities(resource)).containsExactly(activeCapability);
    }

    @Test
    void resourceLookupsAreScopedToImplementationNamespace() {
        SyntheticResource resource = new SyntheticResource();
        Capability httpImplementation = resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", Map.of());
        Capability jaxRsImplementation = resource.addImplementationCapability(JAX_RS_IMPLEMENTATION, "1.0.0", Map.of());
        resource.addCapability("osgi.identity", Map.of(), Map.of("osgi.identity", "sample.bundle"));
        Requirement implementationRequirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        implementationFilter(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", "2.0.0")),
                Map.of());
        resource.addRequirement(
                "osgi.wiring.package",
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=org.osgi.service.http.whiteboard)"),
                Map.of());

        assertThat(resource.getCapabilities(ImplementationNamespace.IMPLEMENTATION_NAMESPACE))
                .containsExactly(httpImplementation, jaxRsImplementation);
        assertThat(resource.getCapabilities(null)).hasSize(3);
        assertThat(resource.getCapabilities("osgi.identity"))
                .extracting(Capability::getNamespace)
                .containsExactly("osgi.identity");
        assertThat(resource.getRequirements(ImplementationNamespace.IMPLEMENTATION_NAMESPACE))
                .containsExactly(implementationRequirement);
        assertThat(resource.getRequirements("osgi.wiring.package")).hasSize(1);
    }

    @Test
    void arbitraryStringAttributesCanRefineImplementationSelection() throws Exception {
        SyntheticResource resource = new SyntheticResource();
        Capability felixHttp = resource.addImplementationCapability(
                HTTP_WHITEBOARD_IMPLEMENTATION,
                "1.1.0",
                Map.of(VENDOR_ATTRIBUTE, "felix", PROFILE_ATTRIBUTE, "servlet", DISTRIBUTION_ATTRIBUTE, "standalone"));
        resource.addImplementationCapability(
                HTTP_WHITEBOARD_IMPLEMENTATION,
                "1.1.0",
                Map.of(VENDOR_ATTRIBUTE, "equinox", PROFILE_ATTRIBUTE, "servlet", DISTRIBUTION_ATTRIBUTE, "equinox"));
        resource.addImplementationCapability(
                TRANSACTION_IMPLEMENTATION,
                "1.0.0",
                Map.of(VENDOR_ATTRIBUTE, "aries", PROFILE_ATTRIBUTE, "jta", DISTRIBUTION_ATTRIBUTE, "standalone"));
        Requirement requirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.implementation=osgi.http)(version>=1.1.0)(vendor=felix)(distribution=standalone))"),
                Map.of());

        assertThat(implementationCapabilitiesMatchingFilter(resource, requirement)).containsExactly(felixHttp);
    }

    @Test
    void substringFiltersCanMatchArbitraryImplementationAttributes() throws Exception {
        SyntheticResource resource = new SyntheticResource();
        Capability standaloneJaxRs = resource.addImplementationCapability(
                JAX_RS_IMPLEMENTATION,
                "1.0.0",
                Map.of(PROFILE_ATTRIBUTE, "jaxrs-whiteboard", DISTRIBUTION_ATTRIBUTE, "standalone-runtime"));
        resource.addImplementationCapability(
                JAX_RS_IMPLEMENTATION,
                "1.0.0",
                Map.of(PROFILE_ATTRIBUTE, "jta-bridge", DISTRIBUTION_ATTRIBUTE, "standalone-runtime"));
        resource.addImplementationCapability(
                HTTP_WHITEBOARD_IMPLEMENTATION,
                "1.1.0",
                Map.of(PROFILE_ATTRIBUTE, "servlet-whiteboard", DISTRIBUTION_ATTRIBUTE, "standalone-runtime"));
        Requirement requirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(" + ImplementationNamespace.IMPLEMENTATION_NAMESPACE + "=" + JAX_RS_IMPLEMENTATION + ")(" +
                                PROFILE_ATTRIBUTE + "=jaxrs-*)(" + DISTRIBUTION_ATTRIBUTE + "=*runtime))"),
                Map.of());

        assertThat(implementationCapabilitiesMatchingFilter(resource, requirement)).containsExactly(standaloneJaxRs);
    }

    @Test
    void qualifiedImplementationVersionParticipatesInVersionRangeSelection() {
        SyntheticResource resource = new SyntheticResource();
        resource.addImplementationCapability(TRANSACTION_IMPLEMENTATION, "1.0.0", Map.of());
        Capability qualifiedImplementation = resource.addImplementationCapability(
                TRANSACTION_IMPLEMENTATION,
                "1.0.0.enterprise",
                Map.of(VENDOR_ATTRIBUTE, "aries"));
        resource.addImplementationCapability(TRANSACTION_IMPLEMENTATION, "1.0.1", Map.of());
        Requirement requirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        implementationFilter(TRANSACTION_IMPLEMENTATION, "1.0.0.enterprise", "1.0.1")),
                Map.of());

        assertThat(implementationCapabilitiesSatisfying(
                resource, requirement, TRANSACTION_IMPLEMENTATION, "1.0.0.enterprise", "1.0.1"))
                .containsExactly(qualifiedImplementation);
        assertThat(capabilityVersion(qualifiedImplementation))
                .isEqualTo(new Version(1, 0, 0, "enterprise"));
    }

    @Test
    void requirementFilterCanSelectAlternativeImplementations() throws Exception {
        SyntheticResource resource = new SyntheticResource();
        Capability httpImplementation = resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", Map.of());
        Capability jaxRsImplementation = resource.addImplementationCapability(JAX_RS_IMPLEMENTATION, "1.0.0", Map.of());
        resource.addImplementationCapability(TRANSACTION_IMPLEMENTATION, "1.0.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(|(" + ImplementationNamespace.IMPLEMENTATION_NAMESPACE + "=" + HTTP_WHITEBOARD_IMPLEMENTATION + ")(" +
                                ImplementationNamespace.IMPLEMENTATION_NAMESPACE + "=" + JAX_RS_IMPLEMENTATION + "))"),
                Map.of());

        assertThat(implementationCapabilitiesMatchingFilter(resource, requirement))
                .containsExactly(httpImplementation, jaxRsImplementation);
    }

    @Test
    void highestCompatibleImplementationCanBeSelectedByVersion() {
        SyntheticResource resource = new SyntheticResource();
        resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", Map.of());
        Capability highestCompatible = resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "1.3.5", Map.of());
        resource.addImplementationCapability(HTTP_WHITEBOARD_IMPLEMENTATION, "2.0.0", Map.of());
        resource.addImplementationCapability(JAX_RS_IMPLEMENTATION, "2.0.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ImplementationNamespace.IMPLEMENTATION_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        implementationFilter(HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", "2.0.0")),
                Map.of());

        assertThat(highestVersion(implementationCapabilitiesSatisfying(
                resource, requirement, HTTP_WHITEBOARD_IMPLEMENTATION, "1.1.0", "2.0.0")))
                .isSameAs(highestCompatible);
    }

    private static String implementationFilter(String implementationName, String floorVersion, String ceilingVersion) {
        return "(&(" + ImplementationNamespace.IMPLEMENTATION_NAMESPACE + "=" + implementationName + ")(" +
                ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + floorVersion + ")(!("
                + ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + ceilingVersion + ")))";
    }

    private static List<Capability> implementationCapabilitiesSatisfying(
            Resource resource,
            Requirement requirement,
            String implementationName,
            String floorVersion,
            String ceilingVersion) {
        assertThat(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                .contains(ImplementationNamespace.IMPLEMENTATION_NAMESPACE + "=" + implementationName)
                .contains(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + floorVersion)
                .contains("!(" + ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + ceilingVersion + ")");
        Version floor = Version.parseVersion(floorVersion);
        Version ceiling = Version.parseVersion(ceilingVersion);
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> Objects.equals(
                        capability.getAttributes().get(ImplementationNamespace.IMPLEMENTATION_NAMESPACE), implementationName))
                .filter(capability -> versionInRange(capabilityVersion(capability), floor, ceiling))
                .collect(Collectors.toList());
    }

    private static List<Capability> implementationCapabilitiesMatchingFilter(
            Resource resource,
            Requirement requirement) throws Exception {
        Filter filter = FrameworkUtil.createFilter(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE));
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> filter.matches(filterAttributes(capability)))
                .collect(Collectors.toList());
    }

    private static Map<String, ?> filterAttributes(Capability capability) {
        Map<String, Object> attributes = new LinkedHashMap<>(capability.getAttributes());
        Object version = attributes.get(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        if (version instanceof Version) {
            attributes.put(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE, version.toString());
        }
        return attributes;
    }

    private static Capability highestVersion(List<Capability> capabilities) {
        return capabilities.stream()
                .max((left, right) -> capabilityVersion(left).compareTo(capabilityVersion(right)))
                .orElseThrow(IllegalStateException::new);
    }

    private static boolean versionInRange(Version version, Version floor, Version ceiling) {
        return version.compareTo(floor) >= 0 && version.compareTo(ceiling) < 0;
    }

    private static Version capabilityVersion(Capability capability) {
        Object value = capability.getAttributes().get(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        assertThat(value).isInstanceOf(Version.class);
        return (Version) value;
    }

    private static List<String> usedPackages(Capability capability) {
        return List.of(capability.getDirectives()
                .getOrDefault(Namespace.CAPABILITY_USES_DIRECTIVE, "")
                .split(","));
    }

    private static List<Capability> resolveTimeImplementationCapabilities(Resource resource) {
        return resource.getCapabilities(ImplementationNamespace.IMPLEMENTATION_NAMESPACE).stream()
                .filter(capability -> Namespace.EFFECTIVE_RESOLVE.equals(capabilityEffectiveDirective(capability)))
                .collect(Collectors.toList());
    }

    private static List<Capability> activeImplementationCapabilities(Resource resource) {
        return resource.getCapabilities(ImplementationNamespace.IMPLEMENTATION_NAMESPACE).stream()
                .filter(capability -> Namespace.EFFECTIVE_ACTIVE.equals(capabilityEffectiveDirective(capability)))
                .collect(Collectors.toList());
    }

    private static String capabilityEffectiveDirective(Capability capability) {
        return capability.getDirectives()
                .getOrDefault(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
    }

    private static List<Requirement> activeImplementationRequirements(Resource resource) {
        return resource.getRequirements(ImplementationNamespace.IMPLEMENTATION_NAMESPACE).stream()
                .filter(requirement -> Namespace.EFFECTIVE_ACTIVE.equals(requirementEffectiveDirective(requirement)))
                .collect(Collectors.toList());
    }

    private static String requirementEffectiveDirective(Requirement requirement) {
        return requirement.getDirectives()
                .getOrDefault(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
    }

    private static String requirementResolutionDirective(Requirement requirement) {
        return requirement.getDirectives()
                .getOrDefault(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_MANDATORY);
    }

    private static String requirementCardinalityDirective(Requirement requirement) {
        return requirement.getDirectives()
                .getOrDefault(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_SINGLE);
    }

    private static final class SyntheticResource implements Resource {
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

        private Capability addImplementationCapability(
                String implementationName,
                String version,
                Map<String, Object> extraAttributes) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put(ImplementationNamespace.IMPLEMENTATION_NAMESPACE, implementationName);
            attributes.put(ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.parseVersion(version));
            attributes.putAll(extraAttributes);
            return addCapability(ImplementationNamespace.IMPLEMENTATION_NAMESPACE, Map.of(), attributes);
        }

        private Capability addCapability(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            Capability capability = new SyntheticCapability(this, namespace, directives, attributes);
            capabilities.add(capability);
            return capability;
        }

        private Requirement addRequirement(
                String namespace,
                Map<String, String> directives,
                Map<String, Object> attributes) {
            Requirement requirement = new SyntheticRequirement(this, namespace, directives, attributes);
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
    }

    private static final class SyntheticCapability implements Capability {
        private final Resource resource;
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;

        private SyntheticCapability(
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
    }

    private static final class SyntheticRequirement implements Requirement {
        private final Resource resource;
        private final String namespace;
        private final Map<String, String> directives;
        private final Map<String, Object> attributes;

        private SyntheticRequirement(
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
    }
}
