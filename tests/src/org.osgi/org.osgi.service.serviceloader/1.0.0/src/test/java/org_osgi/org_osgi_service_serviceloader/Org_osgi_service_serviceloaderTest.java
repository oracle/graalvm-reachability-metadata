/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_serviceloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.osgi.resource.Namespace.REQUIREMENT_FILTER_DIRECTIVE;
import static org.osgi.service.serviceloader.ServiceLoaderNamespace.CAPABILITY_REGISTER_DIRECTIVE;
import static org.osgi.service.serviceloader.ServiceLoaderNamespace.SERVICELOADER_NAMESPACE;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class Org_osgi_service_serviceloaderTest {
    private static final String EXAMPLE_SERVICE_TYPE =
            "org_osgi.org_osgi_service_serviceloader.Org_osgi_service_serviceloaderTest$ExampleService";
    private static final String FIRST_PROVIDER_TYPE =
            "org_osgi.org_osgi_service_serviceloader.Org_osgi_service_serviceloaderTest$FirstProvider";
    private static final String SECOND_PROVIDER_TYPE =
            "org_osgi.org_osgi_service_serviceloader.Org_osgi_service_serviceloaderTest$SecondProvider";
    private static final String INTERNAL_PROVIDER_TYPE =
            "org_osgi.org_osgi_service_serviceloader.Org_osgi_service_serviceloaderTest$InternalProvider";

    @Test
    void serviceLoaderNamespaceIsTheCapabilityAndRequirementNamespaceName() {
        Map<String, Object> capabilityAttributes = new LinkedHashMap<>();

        capabilityAttributes.put(SERVICELOADER_NAMESPACE, EXAMPLE_SERVICE_TYPE);

        assertThat(SERVICELOADER_NAMESPACE).isEqualTo("osgi.serviceloader");
        assertThat(capabilityAttributes)
                .containsEntry("osgi.serviceloader", EXAMPLE_SERVICE_TYPE)
                .hasSize(1);
    }

    @Test
    void requirementFilterMatchesOnlyCapabilitiesForRequestedServiceType() {
        Map<String, Object> requirementDirectives = Map.of(
                REQUIREMENT_FILTER_DIRECTIVE,
                "(" + SERVICELOADER_NAMESPACE + "=" + EXAMPLE_SERVICE_TYPE + ")");
        Map<String, Object> matchingCapability = Map.of(SERVICELOADER_NAMESPACE, EXAMPLE_SERVICE_TYPE);
        Map<String, Object> otherCapability = Map.of(SERVICELOADER_NAMESPACE, "java.lang.Runnable");

        assertThat(REQUIREMENT_FILTER_DIRECTIVE).isEqualTo("filter");
        assertThat(capabilityMatchesRequirement(matchingCapability, requirementDirectives)).isTrue();
        assertThat(capabilityMatchesRequirement(otherCapability, requirementDirectives)).isFalse();
    }

    @Test
    void registerDirectiveCanSelectSpecificProviderImplementations() {
        List<String> advertisedProviders = providerNames(
                new FirstProvider(),
                new SecondProvider(),
                new InternalProvider());
        Map<String, Object> capabilityDirectives = new LinkedHashMap<>();
        capabilityDirectives.put(
                CAPABILITY_REGISTER_DIRECTIVE,
                providerNames(new SecondProvider(), new FirstProvider()));

        List<String> selectedProviders = selectedProviders(advertisedProviders, capabilityDirectives);

        assertThat(CAPABILITY_REGISTER_DIRECTIVE).isEqualTo("register");
        assertThat(selectedProviders)
                .containsExactly(FIRST_PROVIDER_TYPE, SECOND_PROVIDER_TYPE);
    }

    @Test
    void missingRegisterDirectiveMeansAllAdvertisedProvidersAreRegistered() {
        List<String> advertisedProviders = providerNames(new FirstProvider(), new SecondProvider());

        List<String> selectedProviders = selectedProviders(advertisedProviders, Map.of());

        assertThat(selectedProviders)
                .containsExactly(FIRST_PROVIDER_TYPE, SECOND_PROVIDER_TYPE);
    }

    @Test
    void emptyRegisterDirectiveMeansNoProviderIsRegisteredForCapability() {
        List<String> advertisedProviders = providerNames(new FirstProvider(), new SecondProvider());
        Map<String, Object> capabilityDirectives = Map.of(CAPABILITY_REGISTER_DIRECTIVE, List.of());

        List<String> selectedProviders = selectedProviders(advertisedProviders, capabilityDirectives);

        assertThat(selectedProviders).isEmpty();
    }

    @Test
    void serviceLoaderCapabilityKeepsNamespaceAttributeSeparateFromRegisterDirective() {
        Map<String, Object> serviceLoaderCapability = new LinkedHashMap<>();
        serviceLoaderCapability.put(SERVICELOADER_NAMESPACE, EXAMPLE_SERVICE_TYPE);
        serviceLoaderCapability.put("." + SERVICELOADER_NAMESPACE, "private-matching-attribute");
        serviceLoaderCapability.put(CAPABILITY_REGISTER_DIRECTIVE, providerNames(new FirstProvider()));

        assertThat(serviceLoaderCapability)
                .containsEntry(SERVICELOADER_NAMESPACE, EXAMPLE_SERVICE_TYPE)
                .containsEntry(CAPABILITY_REGISTER_DIRECTIVE, providerNames(new FirstProvider()))
                .containsEntry(".osgi.serviceloader", "private-matching-attribute");
    }

    @Test
    void publicMatchingAttributesBecomeServicePropertiesForRegisteredProviders() {
        Map<String, Object> serviceLoaderCapability = new LinkedHashMap<>();
        serviceLoaderCapability.put(SERVICELOADER_NAMESPACE, EXAMPLE_SERVICE_TYPE);
        serviceLoaderCapability.put(CAPABILITY_REGISTER_DIRECTIVE, providerNames(new FirstProvider()));
        serviceLoaderCapability.put("service.scope", "application");
        serviceLoaderCapability.put("service.ranking", 100L);
        serviceLoaderCapability.put("supported.names", List.of("primary", "secondary"));
        serviceLoaderCapability.put(".internal.selector", "native-image-only");

        Map<String, Object> serviceProperties = servicePropertiesForRegisteredProvider(serviceLoaderCapability);

        assertThat(serviceProperties)
                .containsEntry("service.scope", "application")
                .containsEntry("service.ranking", 100L)
                .containsEntry("supported.names", List.of("primary", "secondary"))
                .doesNotContainKeys(
                        SERVICELOADER_NAMESPACE,
                        CAPABILITY_REGISTER_DIRECTIVE,
                        ".internal.selector");
    }

    private static boolean capabilityMatchesRequirement(
            Map<String, Object> capabilityAttributes,
            Map<String, Object> requirementDirectives) {
        String filter = (String) requirementDirectives.get(REQUIREMENT_FILTER_DIRECTIVE);
        if (filter == null) {
            return true;
        }

        String prefix = "(" + SERVICELOADER_NAMESPACE + "=";
        if (!filter.startsWith(prefix) || !filter.endsWith(")")) {
            return false;
        }

        String serviceType = filter.substring(prefix.length(), filter.length() - 1);
        return serviceType.equals(capabilityAttributes.get(SERVICELOADER_NAMESPACE));
    }

    private static List<String> selectedProviders(
            List<String> advertisedProviders,
            Map<String, Object> capabilityDirectives) {
        Object registerDirective = capabilityDirectives.get(CAPABILITY_REGISTER_DIRECTIVE);
        if (registerDirective == null) {
            return advertisedProviders;
        }

        List<?> requestedProviders = (List<?>) registerDirective;
        List<String> selectedProviders = new ArrayList<>();
        for (String advertisedProvider : advertisedProviders) {
            if (requestedProviders.contains(advertisedProvider)) {
                selectedProviders.add(advertisedProvider);
            }
        }
        return selectedProviders;
    }

    private static List<String> providerNames(ExampleService... providers) {
        List<String> names = new ArrayList<>();
        for (ExampleService provider : providers) {
            names.add(provider.providerType());
        }
        return names;
    }

    private static Map<String, Object> servicePropertiesForRegisteredProvider(
            Map<String, Object> serviceLoaderCapability) {
        Map<String, Object> serviceProperties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> attribute : serviceLoaderCapability.entrySet()) {
            String attributeName = attribute.getKey();
            if (!isSpecifiedAttributeOrDirective(attributeName) && !attributeName.startsWith(".")) {
                serviceProperties.put(attributeName, attribute.getValue());
            }
        }
        return serviceProperties;
    }

    private static boolean isSpecifiedAttributeOrDirective(String attributeName) {
        return SERVICELOADER_NAMESPACE.equals(attributeName) || CAPABILITY_REGISTER_DIRECTIVE.equals(attributeName);
    }

    private interface ExampleService {
        String name();

        String providerType();
    }

    private static final class FirstProvider implements ExampleService {
        @Override
        public String name() {
            return "first";
        }

        @Override
        public String providerType() {
            return FIRST_PROVIDER_TYPE;
        }
    }

    private static final class SecondProvider implements ExampleService {
        @Override
        public String name() {
            return "second";
        }

        @Override
        public String providerType() {
            return SECOND_PROVIDER_TYPE;
        }
    }

    private static final class InternalProvider implements ExampleService {
        @Override
        public String name() {
            return "internal";
        }

        @Override
        public String providerType() {
            return INTERNAL_PROVIDER_TYPE;
        }
    }
}
