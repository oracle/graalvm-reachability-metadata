/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;
import org.osgi.annotation.bundle.Attribute;
import org.osgi.annotation.bundle.Capabilities;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Directive;
import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.bundle.Header;
import org.osgi.annotation.bundle.Headers;
import org.osgi.annotation.bundle.Referenced;
import org.osgi.annotation.bundle.Requirement;
import org.osgi.annotation.bundle.Requirements;
import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.annotation.versioning.Version;

public class Osgi_annotationTest {
    @Test
    void versioningAnnotationsSupportPackageAndTypeContracts() {
        Version version = new FixedVersion("2.4.6.qualifier");
        Annotation consumerType = new ConsumerTypeMarker();
        Annotation providerType = new ProviderTypeMarker();
        ConsumerApi consumer = new ConsumerImplementation();
        ProviderApi provider = new ProviderImplementation();

        assertThat(version.value()).isEqualTo("2.4.6.qualifier");
        assertThat(version.annotationType()).isSameAs(Version.class);
        assertThat(consumerType.annotationType()).isSameAs(ConsumerType.class);
        assertThat(providerType.annotationType()).isSameAs(ProviderType.class);
        assertThat(consumer.format("request")).isEqualTo("consumer:request");
        assertThat(provider.provide("request")).isEqualTo("provider:request");
    }

    @Test
    void bundleHeaderAnnotationsRepresentSingleAndRepeatedManifestHeaders() {
        Header category = new FixedHeader("Bundle-Category", "osgi,testing");
        Header activationPolicy = new FixedHeader("Bundle-ActivationPolicy", "lazy");
        Headers headers = new FixedHeaders(category, activationPolicy);

        assertThat(category.name()).isEqualTo("Bundle-Category");
        assertThat(category.value()).isEqualTo("osgi,testing");
        assertThat(category.annotationType()).isSameAs(Header.class);
        assertThat(headers.annotationType()).isSameAs(Headers.class);
        assertThat(headers.value())
                .extracting(Header::name)
                .containsExactly("Bundle-Category", "Bundle-ActivationPolicy");
        assertThat(headers.value())
                .extracting(Header::value)
                .containsExactly("osgi,testing", "lazy");
    }

    @Test
    void capabilityAnnotationsExposeNamespaceUsesAndAdditionalAttributes() {
        Capability capability = new FixedCapability(
                "osgi.extender",
                "osgi.component",
                "1.5.0",
                new Class<?>[] {AnnotatedComponent.class, ProviderImplementation.class},
                "active",
                new String[] {"objectClass:List<String>=com.example.Component", "uses:=org.osgi.service.component"});
        Capabilities capabilities = new FixedCapabilities(
                capability,
                new FixedCapability("osgi.service", "example.Service", "", new Class<?>[0], "resolve", new String[0]));

        assertThat(capability.annotationType()).isSameAs(Capability.class);
        assertThat(capability.namespace()).isEqualTo("osgi.extender");
        assertThat(capability.name()).isEqualTo("osgi.component");
        assertThat(capability.version()).isEqualTo("1.5.0");
        assertThat(capability.uses()).containsExactly(AnnotatedComponent.class, ProviderImplementation.class);
        assertThat(capability.effective()).isEqualTo("active");
        assertThat(capability.attribute())
                .containsExactly("objectClass:List<String>=com.example.Component", "uses:=org.osgi.service.component");
        assertThat(capabilities.annotationType()).isSameAs(Capabilities.class);
        assertThat(capabilities.value()).hasSize(2);
        assertThat(capabilities.value()[1].effective()).isEqualTo("resolve");
    }

    @Test
    void requirementAnnotationsExposeFilterDirectivesAndResolutionPolicies() {
        Requirement requirement = new FixedRequirement(
                "osgi.extender",
                "osgi.component",
                "1.5.0",
                "(component.name=example)",
                "resolve",
                new String[] {"cardinality:=multiple", "target=(objectClass=example.Service)"},
                Requirement.Cardinality.MULTIPLE,
                Requirement.Resolution.OPTIONAL);
        Requirements requirements = new FixedRequirements(
                requirement,
                new FixedRequirement("osgi.ee", "JavaSE", "21", "", "active", new String[0],
                        Requirement.Cardinality.SINGLE, Requirement.Resolution.MANDATORY));

        assertThat(Requirement.Cardinality.SINGLE).isEqualTo("SINGLE");
        assertThat(Requirement.Cardinality.MULTIPLE).isEqualTo("MULTIPLE");
        assertThat(Requirement.Resolution.MANDATORY).isEqualTo("MANDATORY");
        assertThat(Requirement.Resolution.OPTIONAL).isEqualTo("OPTIONAL");
        assertThat(requirement.annotationType()).isSameAs(Requirement.class);
        assertThat(requirement.namespace()).isEqualTo("osgi.extender");
        assertThat(requirement.name()).isEqualTo("osgi.component");
        assertThat(requirement.version()).isEqualTo("1.5.0");
        assertThat(requirement.filter()).isEqualTo("(component.name=example)");
        assertThat(requirement.effective()).isEqualTo("resolve");
        assertThat(requirement.attribute())
                .containsExactly("cardinality:=multiple", "target=(objectClass=example.Service)");
        assertThat(requirement.cardinality()).isEqualTo(Requirement.Cardinality.MULTIPLE);
        assertThat(requirement.resolution()).isEqualTo(Requirement.Resolution.OPTIONAL);
        assertThat(requirements.annotationType()).isSameAs(Requirements.class);
        assertThat(requirements.value()).hasSize(2);
        assertThat(requirements.value()[1].resolution()).isEqualTo(Requirement.Resolution.MANDATORY);
    }

    @Test
    void exportAnnotationExposesUsesAttributesAndSubstitutionPolicies() {
        Export export = new FixedExport(
                new String[] {"org.osgi.framework", "org.osgi.service.component"},
                new String[] {"mandatory:=version", "status=stable"},
                Export.Substitution.PROVIDER);

        assertThat(Export.Substitution.CONSUMER).isEqualTo("CONSUMER");
        assertThat(Export.Substitution.PROVIDER).isEqualTo("PROVIDER");
        assertThat(Export.Substitution.NOIMPORT).isEqualTo("NOIMPORT");
        assertThat(Export.Substitution.CALCULATED).isEqualTo("CALCULATED");
        assertThat(export.annotationType()).isSameAs(Export.class);
        assertThat(export.uses()).containsExactly("org.osgi.framework", "org.osgi.service.component");
        assertThat(export.attribute()).containsExactly("mandatory:=version", "status=stable");
        assertThat(export.substitution()).isEqualTo(Export.Substitution.PROVIDER);
    }

    @Test
    void metaAnnotationsDescribeAttributesDirectivesAndReferencedTypes() {
        Attribute namedAttribute = new FixedAttribute("component.name");
        Attribute defaultAttribute = new FixedAttribute("");
        Directive namedDirective = new FixedDirective("filter");
        Directive defaultDirective = new FixedDirective("");
        Referenced referenced = new FixedReferenced(AnnotatedComponent.class, String.class, Runnable.class);
        ComponentMetadata metadata = new ComponentMetadataImplementation();

        assertThat(namedAttribute.annotationType()).isSameAs(Attribute.class);
        assertThat(namedAttribute.value()).isEqualTo("component.name");
        assertThat(defaultAttribute.value()).isEmpty();
        assertThat(namedDirective.annotationType()).isSameAs(Directive.class);
        assertThat(namedDirective.value()).isEqualTo("filter");
        assertThat(defaultDirective.value()).isEmpty();
        assertThat(referenced.annotationType()).isSameAs(Referenced.class);
        assertThat(referenced.value()).containsExactly(AnnotatedComponent.class, String.class, Runnable.class);
        assertThat(metadata.name()).isEqualTo("example.component");
        assertThat(metadata.filter()).isEqualTo("(enabled=true)");
    }

    @ConsumerType
    private interface ConsumerApi {
        String format(String value);
    }

    private static final class ConsumerImplementation implements ConsumerApi {
        @Override
        public String format(String value) {
            return "consumer:" + value;
        }
    }

    @ProviderType
    private interface ProviderApi {
        String provide(String value);
    }

    @ProviderType
    private static final class ProviderImplementation implements ProviderApi {
        @Override
        public String provide(String value) {
            return "provider:" + value;
        }
    }

    @Header(name = "Bundle-Category", value = "integration-test")
    @Header(name = "Bundle-ActivationPolicy", value = "lazy")
    @Capability(namespace = "osgi.extender", name = "example.component", version = "1.0.0")
    @Capability(namespace = "osgi.service", name = "example.Service", attribute = "objectClass=example.Service")
    @Requirement(namespace = "osgi.ee", name = "JavaSE", version = "21")
    @Requirement(namespace = "osgi.extender", name = "osgi.component", resolution = Requirement.Resolution.OPTIONAL)
    @Referenced({Runnable.class, String.class})
    @ComponentMetadata(name = "example.component", filter = "(enabled=true)")
    private static final class AnnotatedComponent {
    }

    @Capability(namespace = "example.metadata", attribute = "generated=true")
    private @interface ComponentMetadata {
        @Attribute("component.name")
        String name();

        @Directive("filter")
        String filter() default "";
    }

    private static final class ComponentMetadataImplementation implements ComponentMetadata {
        @Override
        public String name() {
            return "example.component";
        }

        @Override
        public String filter() {
            return "(enabled=true)";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ComponentMetadata.class;
        }
    }

    private static final class FixedVersion implements Version {
        private final String value;

        FixedVersion(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Version.class;
        }
    }

    private static final class ConsumerTypeMarker implements ConsumerType {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ConsumerType.class;
        }
    }

    private static final class ProviderTypeMarker implements ProviderType {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ProviderType.class;
        }
    }

    private static final class FixedHeader implements Header {
        private final String name;
        private final String value;

        FixedHeader(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Header.class;
        }
    }

    private static final class FixedHeaders implements Headers {
        private final Header[] value;

        FixedHeaders(Header... value) {
            this.value = value.clone();
        }

        @Override
        public Header[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Headers.class;
        }
    }

    private static final class FixedCapability implements Capability {
        private final String namespace;
        private final String name;
        private final String version;
        private final Class<?>[] uses;
        private final String effective;
        private final String[] attribute;

        FixedCapability(String namespace, String name, String version, Class<?>[] uses, String effective,
                String[] attribute) {
            this.namespace = namespace;
            this.name = name;
            this.version = version;
            this.uses = uses.clone();
            this.effective = effective;
            this.attribute = attribute.clone();
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public Class<?>[] uses() {
            return uses.clone();
        }

        @Override
        public String effective() {
            return effective;
        }

        @Override
        public String[] attribute() {
            return attribute.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Capability.class;
        }
    }

    private static final class FixedCapabilities implements Capabilities {
        private final Capability[] value;

        FixedCapabilities(Capability... value) {
            this.value = value.clone();
        }

        @Override
        public Capability[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Capabilities.class;
        }
    }

    private static final class FixedRequirement implements Requirement {
        private final String namespace;
        private final String name;
        private final String version;
        private final String filter;
        private final String effective;
        private final String[] attribute;
        private final String cardinality;
        private final String resolution;

        FixedRequirement(String namespace, String name, String version, String filter, String effective,
                String[] attribute, String cardinality, String resolution) {
            this.namespace = namespace;
            this.name = name;
            this.version = version;
            this.filter = filter;
            this.effective = effective;
            this.attribute = attribute.clone();
            this.cardinality = cardinality;
            this.resolution = resolution;
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public String filter() {
            return filter;
        }

        @Override
        public String effective() {
            return effective;
        }

        @Override
        public String[] attribute() {
            return attribute.clone();
        }

        @Override
        public String cardinality() {
            return cardinality;
        }

        @Override
        public String resolution() {
            return resolution;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Requirement.class;
        }
    }

    private static final class FixedRequirements implements Requirements {
        private final Requirement[] value;

        FixedRequirements(Requirement... value) {
            this.value = value.clone();
        }

        @Override
        public Requirement[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Requirements.class;
        }
    }

    private static final class FixedExport implements Export {
        private final String[] uses;
        private final String[] attribute;
        private final String substitution;

        FixedExport(String[] uses, String[] attribute, String substitution) {
            this.uses = uses.clone();
            this.attribute = attribute.clone();
            this.substitution = substitution;
        }

        @Override
        public String[] uses() {
            return uses.clone();
        }

        @Override
        public String[] attribute() {
            return attribute.clone();
        }

        @Override
        public String substitution() {
            return substitution;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Export.class;
        }
    }

    private static final class FixedAttribute implements Attribute {
        private final String value;

        FixedAttribute(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Attribute.class;
        }
    }

    private static final class FixedDirective implements Directive {
        private final String value;

        FixedDirective(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Directive.class;
        }
    }

    private static final class FixedReferenced implements Referenced {
        private final Class<?>[] value;

        FixedReferenced(Class<?>... value) {
            this.value = value.clone();
        }

        @Override
        public Class<?>[] value() {
            return value.clone();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Referenced.class;
        }
    }
}
