/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_namespace_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.osgi.namespace.service.ServiceNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class Org_osgi_namespace_serviceTest {
    private static final String PAYMENT_SERVICE = "com.acme.payments.PaymentService";
    private static final String AUDIT_SERVICE = "com.acme.audit.AuditService";
    private static final String INVENTORY_SERVICE = "com.acme.inventory.InventoryService";

    @Test
    void constantsExposeServiceNamespaceContract() {
        assertThat(ServiceNamespace.class).isNotNull();
        assertThat(ServiceNamespace.SERVICE_NAMESPACE).isEqualTo("osgi.service");
        assertThat(ServiceNamespace.SERVICE_NAMESPACE).startsWith("osgi.").doesNotContain(" ");
        assertThat(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE).isEqualTo("objectClass");
        assertThat(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE).isNotEqualTo(ServiceNamespace.SERVICE_NAMESPACE);
    }

    @Test
    void serviceCapabilityCanDeclareMultipleObjectClassesAndUsesDirective() {
        SyntheticResource resource = new SyntheticResource();
        Capability capability = resource.addCapability(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.payments,com.acme.audit"),
                Map.of(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE, List.of(PAYMENT_SERVICE, AUDIT_SERVICE)));

        assertThat(capability.getNamespace()).isEqualTo(ServiceNamespace.SERVICE_NAMESPACE);
        assertThat(capability.getResource()).isSameAs(resource);
        assertThat(capability.getDirectives()).containsEntry(Namespace.CAPABILITY_USES_DIRECTIVE, "com.acme.payments,com.acme.audit");
        assertThat(capability.getAttributes()).containsOnlyKeys(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE);
        assertThat(capability.getAttributes().get(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE))
                .isEqualTo(List.of(PAYMENT_SERVICE, AUDIT_SERVICE));
        assertThatThrownBy(() -> capability.getAttributes().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serviceRequirementUsesObjectClassFilterAndResolutionDirectives() {
        SyntheticResource resource = new SyntheticResource();
        Requirement requirement = resource.addRequirement(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(
                        Namespace.REQUIREMENT_FILTER_DIRECTIVE, objectClassFilter(PAYMENT_SERVICE),
                        Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL,
                        Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE),
                Map.of("component", "checkout"));

        assertThat(requirement.getNamespace()).isEqualTo(ServiceNamespace.SERVICE_NAMESPACE);
        assertThat(requirement.getResource()).isSameAs(resource);
        assertThat(requirement.getDirectives())
                .containsEntry(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(objectClass=" + PAYMENT_SERVICE + ")")
                .containsEntry(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL)
                .containsEntry(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE, Namespace.CARDINALITY_MULTIPLE);
        assertThat(requirement.getAttributes()).containsEntry("component", "checkout");
    }

    @Test
    void resourceFilteringSelectsOnlyServiceNamespaceCapabilitiesAndRequirements() {
        SyntheticResource resource = new SyntheticResource();
        Capability paymentCapability = resource.addCapability(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(),
                Map.of(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE, List.of(PAYMENT_SERVICE)));
        Capability inventoryCapability = resource.addCapability(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(),
                Map.of(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE, List.of(INVENTORY_SERVICE)));
        resource.addCapability("osgi.identity", Map.of(), Map.of("osgi.identity", "sample.bundle"));

        Requirement paymentRequirement = resource.addRequirement(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, objectClassFilter(PAYMENT_SERVICE)),
                Map.of());
        resource.addRequirement("osgi.wiring.package", Map.of(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(osgi.wiring.package=com.acme)"), Map.of());

        assertThat(resource.getCapabilities(ServiceNamespace.SERVICE_NAMESPACE)).containsExactly(paymentCapability, inventoryCapability);
        assertThat(resource.getCapabilities(null)).hasSize(3);
        assertThat(resource.getCapabilities("osgi.identity")).extracting(Capability::getNamespace).containsExactly("osgi.identity");
        assertThat(resource.getRequirements(ServiceNamespace.SERVICE_NAMESPACE)).containsExactly(paymentRequirement);
        assertThat(resource.getRequirements("osgi.wiring.package")).hasSize(1);
    }

    @Test
    void objectClassAttributeSupportsSimpleServiceSelection() {
        SyntheticResource resource = new SyntheticResource();
        resource.addCapability(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(),
                Map.of(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE, List.of(PAYMENT_SERVICE, AUDIT_SERVICE)));
        resource.addCapability(
                ServiceNamespace.SERVICE_NAMESPACE,
                Map.of(),
                Map.of(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE, List.of(INVENTORY_SERVICE)));

        List<Capability> paymentProviders = serviceCapabilitiesProviding(resource, PAYMENT_SERVICE);
        List<Capability> auditProviders = serviceCapabilitiesProviding(resource, AUDIT_SERVICE);
        List<Capability> missingProviders = serviceCapabilitiesProviding(resource, "com.acme.missing.MissingService");

        assertThat(paymentProviders).hasSize(1);
        assertThat(auditProviders).containsExactlyElementsOf(paymentProviders);
        assertThat(missingProviders).isEmpty();
        assertThat(paymentProviders.get(0).getAttributes().get(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE))
                .isEqualTo(List.of(PAYMENT_SERVICE, AUDIT_SERVICE));
    }

    private static String objectClassFilter(String objectClass) {
        return "(" + ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE + "=" + objectClass + ")";
    }

    private static List<Capability> serviceCapabilitiesProviding(Resource resource, String objectClass) {
        return resource.getCapabilities(ServiceNamespace.SERVICE_NAMESPACE).stream()
                .filter(capability -> objectClassValues(capability).contains(objectClass))
                .collect(Collectors.toList());
    }

    private static List<String> objectClassValues(Capability capability) {
        Object value = capability.getAttributes().get(ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE);
        assertThat(value).isInstanceOf(List.class);
        return ((List<?>) value).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private static final class SyntheticResource implements Resource {
        private final List<Capability> capabilities = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();

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
