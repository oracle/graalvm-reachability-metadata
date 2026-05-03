/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_namespace_contract;

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
import org.osgi.namespace.contract.ContractNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class Org_osgi_namespace_contractTest {
    private static final String JAKARTA_SERVLET_CONTRACT = "JakartaServlet";
    private static final String JAX_RS_CONTRACT = "JavaJAXRS";
    private static final String CDI_CONTRACT = "JavaCDI";
    private static final String IMPLEMENTATION_ATTRIBUTE = "implementation";
    private static final String RUNTIME_ATTRIBUTE = "runtime";
    private static final String PROFILE_ATTRIBUTE = "profile";

    @Test
    void constantsExposeContractNamespaceContract() {
        assertThat(ContractNamespace.class).isNotNull();
        assertThat(ContractNamespace.CONTRACT_NAMESPACE).isEqualTo("osgi.contract");
        assertThat(ContractNamespace.CONTRACT_NAMESPACE).startsWith("osgi.").doesNotContain(" ");
        assertThat(ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE).isEqualTo("version");
        assertThat(ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE).isNotEqualTo(ContractNamespace.CONTRACT_NAMESPACE);
    }

    @Test
    void contractCapabilityUsesNamespaceAsNameAttributeAndVersionAttribute() {
        SyntheticResource resource = new SyntheticResource();
        Version specificationVersion = Version.parseVersion("5.0.0");
        Capability capability = resource.addCapability(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "jakarta.servlet,jakarta.servlet.http"),
                Map.of(
                        ContractNamespace.CONTRACT_NAMESPACE, JAKARTA_SERVLET_CONTRACT,
                        ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE, specificationVersion,
                        IMPLEMENTATION_ATTRIBUTE, "equinox"));

        assertThat(capability.getNamespace()).isEqualTo(ContractNamespace.CONTRACT_NAMESPACE);
        assertThat(capability.getResource()).isSameAs(resource);
        assertThat(capability.getDirectives())
                .containsEntry(Namespace.CAPABILITY_USES_DIRECTIVE, "jakarta.servlet,jakarta.servlet.http");
        assertThat(capability.getAttributes())
                .containsEntry(ContractNamespace.CONTRACT_NAMESPACE, JAKARTA_SERVLET_CONTRACT)
                .containsEntry(ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE, specificationVersion)
                .containsEntry(IMPLEMENTATION_ATTRIBUTE, "equinox");
        assertThat(capabilityVersion(capability)).isEqualTo(new Version(5, 0, 0));
        assertThat(usedPackages(capability)).containsExactly("jakarta.servlet", "jakarta.servlet.http");
        assertThatThrownBy(() -> capability.getAttributes().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void contractRequirementUsesNamespaceAndVersionRangeFilter() {
        SyntheticResource resource = new SyntheticResource();
        Capability minimumCompatible = resource.addContractCapability(JAKARTA_SERVLET_CONTRACT, "5.0.0", Map.of());
        Capability preferredCompatible = resource.addContractCapability(JAKARTA_SERVLET_CONTRACT, "5.0.1", Map.of());
        resource.addContractCapability(JAKARTA_SERVLET_CONTRACT, "6.0.0", Map.of());
        resource.addContractCapability(JAX_RS_CONTRACT, "3.0.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        contractFilter(JAKARTA_SERVLET_CONTRACT, "5.0.0", "6.0.0"),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE,
                        Namespace.RESOLUTION_MANDATORY),
                Map.of("consumer", "web.application"));

        assertThat(requirement.getNamespace()).isEqualTo(ContractNamespace.CONTRACT_NAMESPACE);
        assertThat(requirement.getResource()).isSameAs(resource);
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.contract=JakartaServlet)(version>=5.0.0)(!(version>=6.0.0)))")
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_MANDATORY);
        assertThat(requirement.getAttributes()).containsEntry("consumer", "web.application");
        assertThat(contractCapabilitiesSatisfying(
                resource, requirement, JAKARTA_SERVLET_CONTRACT, "5.0.0", "6.0.0"))
                .containsExactly(minimumCompatible, preferredCompatible);
    }

    @Test
    void optionalActiveRequirementIsKeptSeparateFromResolveTimeRequirement() {
        SyntheticResource resource = new SyntheticResource();
        Requirement resolveRequirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, contractFilter(JAKARTA_SERVLET_CONTRACT, "5.0.0", "6.0.0")),
                Map.of());
        Requirement activeRequirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE, contractFilter(JAX_RS_CONTRACT, "3.0.0", "4.0.0"),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL,
                        Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE,
                        Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE),
                Map.of());

        assertThat(requirementEffectiveDirective(resolveRequirement)).isEqualTo(Namespace.EFFECTIVE_RESOLVE);
        assertThat(requirementResolutionDirective(resolveRequirement)).isEqualTo(Namespace.RESOLUTION_MANDATORY);
        assertThat(requirementCardinalityDirective(resolveRequirement)).isEqualTo(Namespace.CARDINALITY_SINGLE);
        assertThat(activeContractRequirements(resource)).containsExactly(activeRequirement);
        assertThat(activeRequirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL)
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
    }

    @Test
    void activeContractCapabilityIsDistinguishedFromResolveTimeCapabilities() {
        SyntheticResource resource = new SyntheticResource();
        Capability resolveCapability = resource.addContractCapability(JAKARTA_SERVLET_CONTRACT, "5.0.0", Map.of());
        Capability activeCapability = resource.addCapability(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE),
                Map.of(
                        ContractNamespace.CONTRACT_NAMESPACE, JAX_RS_CONTRACT,
                        ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.parseVersion("3.1.0")));

        assertThat(activeCapability.getDirectives())
                .containsEntry(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_ACTIVE);
        assertThat(resolveTimeContractCapabilities(resource)).containsExactly(resolveCapability);
        assertThat(activeContractCapabilities(resource)).containsExactly(activeCapability);
    }

    @Test
    void resourceLookupsAreScopedToContractNamespace() {
        SyntheticResource resource = new SyntheticResource();
        Capability servletContract = resource.addContractCapability(JAKARTA_SERVLET_CONTRACT, "5.0.0", Map.of());
        Capability jaxRsContract = resource.addContractCapability(JAX_RS_CONTRACT, "3.1.0", Map.of());
        resource.addCapability("osgi.identity", Map.of(), Map.of("osgi.identity", "sample.bundle"));
        Requirement contractRequirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, contractFilter(JAKARTA_SERVLET_CONTRACT, "5.0.0", "6.0.0")),
                Map.of());
        resource.addRequirement(
                "osgi.wiring.package",
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=jakarta.servlet)"),
                Map.of());

        assertThat(resource.getCapabilities(ContractNamespace.CONTRACT_NAMESPACE))
                .containsExactly(servletContract, jaxRsContract);
        assertThat(resource.getCapabilities(null)).hasSize(3);
        assertThat(resource.getCapabilities("osgi.identity"))
                .extracting(Capability::getNamespace)
                .containsExactly("osgi.identity");
        assertThat(resource.getRequirements(ContractNamespace.CONTRACT_NAMESPACE)).containsExactly(contractRequirement);
        assertThat(resource.getRequirements("osgi.wiring.package")).hasSize(1);
    }

    @Test
    void arbitraryStringAttributesCanRefineContractSelection() {
        SyntheticResource resource = new SyntheticResource();
        Capability standaloneServlet = resource.addContractCapability(
                JAKARTA_SERVLET_CONTRACT,
                "5.0.0",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "jetty", RUNTIME_ATTRIBUTE, "standalone", PROFILE_ATTRIBUTE, "web"));
        resource.addContractCapability(
                JAKARTA_SERVLET_CONTRACT,
                "5.0.0",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "tomcat", RUNTIME_ATTRIBUTE, "embedded", PROFILE_ATTRIBUTE, "web"));
        resource.addContractCapability(
                CDI_CONTRACT,
                "4.0.0",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "weld", RUNTIME_ATTRIBUTE, "standalone", PROFILE_ATTRIBUTE, "di"));
        Requirement requirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(&(osgi.contract=JakartaServlet)(version>=5.0.0)(implementation=jetty)(runtime=standalone))"),
                Map.of());

        assertThat(contractCapabilitiesWithAttributes(resource, requirement, Map.of(
                ContractNamespace.CONTRACT_NAMESPACE, JAKARTA_SERVLET_CONTRACT,
                IMPLEMENTATION_ATTRIBUTE, "jetty",
                RUNTIME_ATTRIBUTE, "standalone"))).containsExactly(standaloneServlet);
    }

    @Test
    void frameworkFilterCanSelectAlternativeContracts() throws Exception {
        SyntheticResource resource = new SyntheticResource();
        Capability servletContract = resource.addContractCapability(JAKARTA_SERVLET_CONTRACT, "5.0.0", Map.of());
        Capability jaxRsContract = resource.addContractCapability(JAX_RS_CONTRACT, "3.1.0", Map.of());
        resource.addContractCapability(CDI_CONTRACT, "4.0.0", Map.of());
        Requirement requirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
                        "(|(" + ContractNamespace.CONTRACT_NAMESPACE + "=" + JAKARTA_SERVLET_CONTRACT + ")(" +
                                ContractNamespace.CONTRACT_NAMESPACE + "=" + JAX_RS_CONTRACT + "))"),
                Map.of());

        assertThat(contractCapabilitiesMatchingFilter(resource, requirement)).containsExactly(servletContract, jaxRsContract);
    }

    @Test
    void qualifiedContractVersionParticipatesInVersionRangeSelection() {
        SyntheticResource resource = new SyntheticResource();
        resource.addContractCapability(CDI_CONTRACT, "4.0.0", Map.of());
        Capability qualifiedContract = resource.addContractCapability(
                CDI_CONTRACT,
                "4.0.0.enterprise",
                Map.of(IMPLEMENTATION_ATTRIBUTE, "weld"));
        resource.addContractCapability(CDI_CONTRACT, "4.0.1", Map.of());
        Requirement requirement = resource.addRequirement(
                ContractNamespace.CONTRACT_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, contractFilter(CDI_CONTRACT, "4.0.0.enterprise", "4.0.1")),
                Map.of());

        assertThat(contractCapabilitiesSatisfying(resource, requirement, CDI_CONTRACT, "4.0.0.enterprise", "4.0.1"))
                .containsExactly(qualifiedContract);
        assertThat(capabilityVersion(qualifiedContract)).isEqualTo(new Version(4, 0, 0, "enterprise"));
    }

    private static String contractFilter(String contractName, String floorVersion, String ceilingVersion) {
        return "(&(" + ContractNamespace.CONTRACT_NAMESPACE + "=" + contractName + ")(" +
                ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + floorVersion + ")(!(" +
                ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + ceilingVersion + ")))";
    }

    private static List<Capability> contractCapabilitiesSatisfying(
            Resource resource,
            Requirement requirement,
            String contractName,
            String floorVersion,
            String ceilingVersion) {
        assertThat(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                .contains(ContractNamespace.CONTRACT_NAMESPACE + "=" + contractName)
                .contains(ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + floorVersion)
                .contains("!(" + ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE + ">=" + ceilingVersion + ")");
        Version floor = Version.parseVersion(floorVersion);
        Version ceiling = Version.parseVersion(ceilingVersion);
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> Objects.equals(capability.getAttributes().get(ContractNamespace.CONTRACT_NAMESPACE), contractName))
                .filter(capability -> versionInRange(capabilityVersion(capability), floor, ceiling))
                .collect(Collectors.toList());
    }

    private static List<Capability> contractCapabilitiesWithAttributes(
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

    private static List<Capability> contractCapabilitiesMatchingFilter(Resource resource, Requirement requirement) throws Exception {
        Filter filter = FrameworkUtil.createFilter(requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE));
        return resource.getCapabilities(requirement.getNamespace()).stream()
                .filter(capability -> filter.matches(capability.getAttributes()))
                .collect(Collectors.toList());
    }

    private static boolean versionInRange(Version version, Version floor, Version ceiling) {
        return version.compareTo(floor) >= 0 && version.compareTo(ceiling) < 0;
    }

    private static Version capabilityVersion(Capability capability) {
        Object value = capability.getAttributes().get(ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE);
        assertThat(value).isInstanceOf(Version.class);
        return (Version) value;
    }

    private static List<String> usedPackages(Capability capability) {
        String uses = capability.getDirectives().get(Namespace.CAPABILITY_USES_DIRECTIVE);
        assertThat(uses).isNotBlank();
        return List.of(uses.split(","));
    }

    private static List<Capability> resolveTimeContractCapabilities(Resource resource) {
        return resource.getCapabilities(ContractNamespace.CONTRACT_NAMESPACE).stream()
                .filter(capability -> Namespace.EFFECTIVE_RESOLVE.equals(capabilityEffectiveDirective(capability)))
                .collect(Collectors.toList());
    }

    private static List<Capability> activeContractCapabilities(Resource resource) {
        return resource.getCapabilities(ContractNamespace.CONTRACT_NAMESPACE).stream()
                .filter(capability -> Namespace.EFFECTIVE_ACTIVE.equals(capabilityEffectiveDirective(capability)))
                .collect(Collectors.toList());
    }

    private static List<Requirement> activeContractRequirements(Resource resource) {
        return resource.getRequirements(ContractNamespace.CONTRACT_NAMESPACE).stream()
                .filter(requirement -> Namespace.EFFECTIVE_ACTIVE.equals(requirementEffectiveDirective(requirement)))
                .collect(Collectors.toList());
    }

    private static String capabilityEffectiveDirective(Capability capability) {
        return capability.getDirectives().getOrDefault(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
    }

    private static String requirementEffectiveDirective(Requirement requirement) {
        return requirement.getDirectives().getOrDefault(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE, Namespace.EFFECTIVE_RESOLVE);
    }

    private static String requirementResolutionDirective(Requirement requirement) {
        return requirement.getDirectives().getOrDefault(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_MANDATORY);
    }

    private static String requirementCardinalityDirective(Requirement requirement) {
        return requirement.getDirectives().getOrDefault(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_SINGLE);
    }

    private static final class SyntheticResource implements Resource {
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

        Capability addContractCapability(String contractName, String version, Map<String, String> additionalAttributes) {
            LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
            attributes.put(ContractNamespace.CONTRACT_NAMESPACE, contractName);
            attributes.put(ContractNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.parseVersion(version));
            attributes.putAll(additionalAttributes);
            return addCapability(ContractNamespace.CONTRACT_NAMESPACE, Map.of(), attributes);
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
