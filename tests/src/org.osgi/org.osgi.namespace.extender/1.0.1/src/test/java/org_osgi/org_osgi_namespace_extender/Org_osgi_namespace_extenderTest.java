/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_namespace_extender;

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
import org.osgi.namespace.extender.ExtenderNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class Org_osgi_namespace_extenderTest {
    private static final String DECLARATIVE_SERVICES_EXTENDER = "osgi.component";
    private static final String BLUEPRINT_EXTENDER = "osgi.blueprint";
    private static final String WEB_EXTENDER = "osgi.web";
    private static final String IMPLEMENTATION_ATTRIBUTE = "implementation";
    private static final String RUNTIME_ATTRIBUTE = "runtime";

    @Test
    void constantsExposeExtenderNamespaceContract() {
        assertThat(ExtenderNamespace.class).isNotNull();
        assertThat(ExtenderNamespace.EXTENDER_NAMESPACE).isEqualTo("osgi.extender");
        assertThat(ExtenderNamespace.EXTENDER_NAMESPACE).startsWith("osgi.").doesNotContain(" ");
        assertThat(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE).isEqualTo("version");
        assertThat(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE).isNotEqualTo(ExtenderNamespace.EXTENDER_NAMESPACE);
    }

    @Test
    void extenderCapabilityUsesNamespaceAsNameAttributeAndVersionAttribute() {
        SyntheticResource resource = new SyntheticResource();
        Version extenderVersion = Version.parseVersion("1.5.0");
        Capability capability = resource.addCapability(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "org.osgi.service.component"),
                Map.of(
                        ExtenderNamespace.EXTENDER_NAMESPACE, DECLARATIVE_SERVICES_EXTENDER,
                        ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE, extenderVersion,
                        IMPLEMENTATION_ATTRIBUTE, "felix-scr"));

        assertThat(capability.getNamespace()).isEqualTo(ExtenderNamespace.EXTENDER_NAMESPACE);
        assertThat(capability.getResource()).isSameAs(resource);
        assertThat(capability.getDirectives()).containsEntry(Namespace.CAPABILITY_USES_DIRECTIVE, "org.osgi.service.component");
        assertThat(capability.getAttributes())
                .containsEntry(ExtenderNamespace.EXTENDER_NAMESPACE, DECLARATIVE_SERVICES_EXTENDER)
                .containsEntry(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE, extenderVersion)
                .containsEntry(IMPLEMENTATION_ATTRIBUTE, "felix-scr");
        assertThat(capabilityVersion(capability)).isEqualTo(new Version(1, 5, 0));
        assertThatThrownBy(() -> capability.getAttributes().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void extenderRequirementUsesNamespaceAndVersionRangeFilter() {
        SyntheticResource resource = new SyntheticResource();
        Capability firstCompatible = resource.addExtenderCapability(DECLARATIVE_SERVICES_EXTENDER, "1.3.0", Map.of());
        Capability preferredCompatible = resource.addExtenderCapability(DECLARATIVE_SERVICES_EXTENDER, "1.5.0", Map.of());
        resource.addExtenderCapability(DECLARATIVE_SERVICES_EXTENDER, "2.0.0", Map.of());
        resource.addExtenderCapability(BLUEPRINT_EXTENDER, "1.0.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        extenderFilter(DECLARATIVE_SERVICES_EXTENDER, "1.3.0", "2.0.0"),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE,
                        Namespace.RESOLUTION_MANDATORY),
                Map.of("consumer", "inventory.bundle"));

        assertThat(requirement.getNamespace()).isEqualTo(ExtenderNamespace.EXTENDER_NAMESPACE);
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.extender=osgi.component)(version>=1.3.0)(!(version>=2.0.0)))")
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_MANDATORY);
        assertThat(requirement.getAttributes()).containsEntry("consumer", "inventory.bundle");
        assertThat(extenderCapabilitiesSatisfying(resource, requirement, DECLARATIVE_SERVICES_EXTENDER, "1.3.0", "2.0.0"))
                .containsExactly(firstCompatible, preferredCompatible);
    }

    @Test
    void optionalActiveRequirementIsKeptSeparateFromResolveTimeRequirement() {
        SyntheticResource resource = new SyntheticResource();
        Requirement resolveRequirement = resource.addRequirement(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, extenderFilter(DECLARATIVE_SERVICES_EXTENDER, "1.4.0", "2.0.0")),
                Map.of());
        Requirement activeRequirement = resource.addRequirement(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE, extenderFilter(BLUEPRINT_EXTENDER, "1.0.0", "2.0.0"),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL,
                        Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE,
                        Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE),
                Map.of());

        assertThat(requirementEffectiveDirective(resolveRequirement)).isEqualTo(Namespace.EFFECTIVE_RESOLVE);
        assertThat(requirementResolutionDirective(resolveRequirement)).isEqualTo(Namespace.RESOLUTION_MANDATORY);
        assertThat(activeExtenderRequirements(resource)).containsExactly(activeRequirement);
        assertThat(activeRequirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL)
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
    }

    @Test
    void activeExtenderCapabilityIsDistinguishedFromResolveTimeCapabilities() {
        SyntheticResource resource = new SyntheticResource();
        Capability resolveCapability = resource.addExtenderCapability(DECLARATIVE_SERVICES_EXTENDER, "1.5.0", Map.of());
        Capability activeCapability = resource.addCapability(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(ExtenderNamespace.CAPABILITY_EFFECTIVE_DIRECTIVE, ExtenderNamespace.EFFECTIVE_ACTIVE),
                Map.of(
                        ExtenderNamespace.EXTENDER_NAMESPACE, BLUEPRINT_EXTENDER,
                        ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.parseVersion("1.0.0")));

        assertThat(activeCapability.getDirectives())
                .containsEntry(ExtenderNamespace.CAPABILITY_EFFECTIVE_DIRECTIVE, ExtenderNamespace.EFFECTIVE_ACTIVE);
        assertThat(resolveTimeExtenderCapabilities(resource)).containsExactly(resolveCapability);
        assertThat(activeExtenderCapabilities(resource)).containsExactly(activeCapability);
    }

    @Test
    void resourceLookupsAreScopedToExtenderNamespace() {
        SyntheticResource resource = new SyntheticResource();
        Capability declarativeServices = resource.addExtenderCapability(DECLARATIVE_SERVICES_EXTENDER, "1.4.0", Map.of());
        Capability blueprint = resource.addExtenderCapability(BLUEPRINT_EXTENDER, "1.0.0", Map.of());
        resource.addCapability("osgi.identity", Map.of(), Map.of("osgi.identity", "sample.bundle"));
        Requirement extenderRequirement = resource.addRequirement(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, extenderFilter(DECLARATIVE_SERVICES_EXTENDER, "1.4.0", "2.0.0")),
                Map.of());
        resource.addRequirement("osgi.wiring.package", Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=org.example)"), Map.of());

        assertThat(resource.getCapabilities(ExtenderNamespace.EXTENDER_NAMESPACE)).containsExactly(declarativeServices, blueprint);
        assertThat(resource.getCapabilities(null)).hasSize(3);
        assertThat(resource.getCapabilities("osgi.identity")).extracting(Capability::getNamespace).containsExactly("osgi.identity");
        assertThat(resource.getRequirements(ExtenderNamespace.EXTENDER_NAMESPACE)).containsExactly(extenderRequirement);
        assertThat(resource.getRequirements("osgi.wiring.package")).hasSize(1);
    }

    @Test
    void arbitraryStringAttributesCanRefineExtenderSelection() {
        SyntheticResource resource = new SyntheticResource();
        Capability felixDeclarativeServices = resource.addExtenderCapability(
                DECLARATIVE_SERVICES_EXTENDER,
                "1.5.0",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "felix-scr", RUNTIME_ATTRIBUTE, "standalone"));
        resource.addExtenderCapability(
                DECLARATIVE_SERVICES_EXTENDER,
                "1.5.0",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "equinox-ds", RUNTIME_ATTRIBUTE, "equinox"));
        resource.addExtenderCapability(
                WEB_EXTENDER,
                "1.0.0",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "pax-web", RUNTIME_ATTRIBUTE, "jetty"));
        Requirement requirement = resource.addRequirement(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.extender=osgi.component)(version>=1.5.0)(implementation=felix-scr)(runtime=standalone))"),
                Map.of());

        assertThat(extenderCapabilitiesWithAttributes(resource, requirement, Map.of(
                ExtenderNamespace.EXTENDER_NAMESPACE, DECLARATIVE_SERVICES_EXTENDER,
                IMPLEMENTATION_ATTRIBUTE, "felix-scr",
                RUNTIME_ATTRIBUTE, "standalone"))).containsExactly(felixDeclarativeServices);
    }

    @Test
    void requirementFilterCanSelectAlternativeExtenders() throws Exception {
        SyntheticResource resource = new SyntheticResource();
        Capability declarativeServices = resource.addExtenderCapability(DECLARATIVE_SERVICES_EXTENDER, "1.5.0", Map.of());
        Capability blueprint = resource.addExtenderCapability(BLUEPRINT_EXTENDER, "1.0.0", Map.of());
        resource.addExtenderCapability(WEB_EXTENDER, "1.0.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ExtenderNamespace.EXTENDER_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(|(" + ExtenderNamespace.EXTENDER_NAMESPACE + "=" + DECLARATIVE_SERVICES_EXTENDER + ")("
                                + ExtenderNamespace.EXTENDER_NAMESPACE + "=" + BLUEPRINT_EXTENDER + "))"),
                Map.of());

        assertThat(extenderCapabilitiesMatchingFilter(resource, requirement)).containsExactly(declarativeServices, blueprint);
    }

    private static String extenderFilter(String extenderName, String floorVersion, String ceilingVersion) {
        return "(&(" + ExtenderNamespace.EXTENDER_NAMESPACE + "=" + extenderName + ")(" +
                ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + floorVersion + ")(!(" +
                ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + ceilingVersion + ")))";
    }

    private static List<Capability> extenderCapabilitiesSatisfying(
            Resource resource,
            Requirement requirement,
            String extenderName,
            String floorVersion,
            String ceilingVersion) {
        assertThat(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                .contains(ExtenderNamespace.EXTENDER_NAMESPACE + "=" + extenderName)
                .contains(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + floorVersion)
                .contains("!(" + ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + ceilingVersion + ")");
        Version floor = Version.parseVersion(floorVersion);
        Version ceiling = Version.parseVersion(ceilingVersion);
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> Objects.equals(capability.getAttributes().get(ExtenderNamespace.EXTENDER_NAMESPACE), extenderName))
                .filter(capability -> versionInRange(capabilityVersion(capability), floor, ceiling))
                .collect(Collectors.toList());
    }

    private static List<Capability> extenderCapabilitiesWithAttributes(
            Resource resource,
            Requirement requirement,
            Map<String, String> expectedAttributes) {
        String filter = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        expectedAttributes.forEach((attributeName, expectedValue) -> assertThat(filter).contains(attributeName + "=" + expectedValue));
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> expectedAttributes.entrySet().stream()
                        .allMatch(entry -> Objects.equals(capability.getAttributes().get(entry.getKey()), entry.getValue())))
                .collect(Collectors.toList());
    }

    private static List<Capability> extenderCapabilitiesMatchingFilter(Resource resource, Requirement requirement) throws Exception {
        Filter filter = FrameworkUtil.createFilter(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE));
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> filter.matches(capability.getAttributes()))
                .collect(Collectors.toList());
    }

    private static boolean versionInRange(Version version, Version floor, Version ceiling) {
        return version.compareTo(floor) >= 0 && version.compareTo(ceiling) < 0;
    }

    private static Version capabilityVersion(Capability capability) {
        Object value = capability.getAttributes().get(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        assertThat(value).isInstanceOf(Version.class);
        return (Version) value;
    }

    private static List<Capability> resolveTimeExtenderCapabilities(Resource resource) {
        return resource.getCapabilities(ExtenderNamespace.EXTENDER_NAMESPACE).stream()
                .filter(capability -> ExtenderNamespace.EFFECTIVE_RESOLVE.equals(capabilityEffectiveDirective(capability)))
                .collect(Collectors.toList());
    }

    private static List<Capability> activeExtenderCapabilities(Resource resource) {
        return resource.getCapabilities(ExtenderNamespace.EXTENDER_NAMESPACE).stream()
                .filter(capability -> ExtenderNamespace.EFFECTIVE_ACTIVE.equals(capabilityEffectiveDirective(capability)))
                .collect(Collectors.toList());
    }

    private static String capabilityEffectiveDirective(Capability capability) {
        return capability.getDirectives()
                .getOrDefault(ExtenderNamespace.CAPABILITY_EFFECTIVE_DIRECTIVE, ExtenderNamespace.EFFECTIVE_RESOLVE);
    }

    private static List<Requirement> activeExtenderRequirements(Resource resource) {
        return resource.getRequirements(ExtenderNamespace.EXTENDER_NAMESPACE).stream()
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

    private static final class SyntheticResource implements Resource {
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

        Capability addExtenderCapability(String extenderName, String version, Map<String, Object> extraAttributes) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put(ExtenderNamespace.EXTENDER_NAMESPACE, extenderName);
            attributes.put(ExtenderNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.parseVersion(version));
            attributes.putAll(extraAttributes);
            return addCapability(ExtenderNamespace.EXTENDER_NAMESPACE, Map.of(), attributes);
        }

        Capability addCapability(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
            Capability capability = new SyntheticCapability(this, namespace, directives, attributes);
            capabilities.add(capability);
            return capability;
        }

        Requirement addRequirement(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
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

        private SyntheticCapability(Resource resource, String namespace, Map<String, String> directives, Map<String, Object> attributes) {
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

        private SyntheticRequirement(Resource resource, String namespace, Map<String, String> directives, Map<String, Object> attributes) {
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
