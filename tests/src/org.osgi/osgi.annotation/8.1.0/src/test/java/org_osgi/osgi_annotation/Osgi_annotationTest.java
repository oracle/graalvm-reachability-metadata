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
    void versioningAnnotationsCanDescribeConsumerAndProviderContracts() {
        ConsumerService consumer = new ConsumerServiceImplementation();
        ProviderService provider = new ProviderServiceImplementation();
        Version version = new FixedVersion("8.1.0");
        Annotation consumerMarker = new ConsumerTypeMarker();
        Annotation providerMarker = new ProviderTypeMarker();

        assertThat(consumer.format("request")).isEqualTo("consumer:request");
        assertThat(provider.process("payload")).isEqualTo("provider:payload");
        assertThat(version.annotationType()).isSameAs(Version.class);
        assertThat(version.value()).isEqualTo("8.1.0");
        assertThat(consumerMarker.annotationType()).isSameAs(ConsumerType.class);
        assertThat(providerMarker.annotationType()).isSameAs(ProviderType.class);
    }

    @Test
    void capabilityAnnotationExposesAllManifestClauseElements() {
        Capability capability = new FixedCapability(
                "osgi.extender",
                "sample.extender",
                "1.2.3",
                new Class<?>[] {ConsumerService.class, ProviderService.class },
                "active",
                new String[] {"service=sample", "ranking:Long=10", "uses:=org.example" });

        assertThat(capability.annotationType()).isSameAs(Capability.class);
        assertThat(capability.namespace()).isEqualTo("osgi.extender");
        assertThat(capability.name()).isEqualTo("sample.extender");
        assertThat(capability.version()).isEqualTo("1.2.3");
        assertThat(capability.uses()).containsExactly(ConsumerService.class, ProviderService.class);
        assertThat(capability.effective()).isEqualTo("active");
        assertThat(capability.attribute())
                .containsExactly("service=sample", "ranking:Long=10", "uses:=org.example");
    }

    @Test
    void requirementAnnotationExposesFilteringCardinalityAndResolutionElements() {
        Requirement requirement = new FixedRequirement(
                "osgi.service",
                "org.example.Service",
                "2.0.0",
                "(&(objectClass=org.example.Service)(mode=test))",
                "resolve",
                new String[] {"target=test", "priority:Integer=5" },
                Requirement.Cardinality.MULTIPLE,
                Requirement.Resolution.OPTIONAL);

        assertThat(requirement.annotationType()).isSameAs(Requirement.class);
        assertThat(requirement.namespace()).isEqualTo("osgi.service");
        assertThat(requirement.name()).isEqualTo("org.example.Service");
        assertThat(requirement.version()).isEqualTo("2.0.0");
        assertThat(requirement.filter()).isEqualTo("(&(objectClass=org.example.Service)(mode=test))");
        assertThat(requirement.effective()).isEqualTo("resolve");
        assertThat(requirement.attribute()).containsExactly("target=test", "priority:Integer=5");
        assertThat(requirement.cardinality()).isEqualTo("MULTIPLE");
        assertThat(requirement.resolution()).isEqualTo("OPTIONAL");
        assertThat(Requirement.Cardinality.SINGLE).isEqualTo("SINGLE");
        assertThat(Requirement.Resolution.MANDATORY).isEqualTo("MANDATORY");
    }

    @Test
    void directRepeatableAnnotationsCanDeclareMultipleBundleClausesOnOneType() {
        RepeatableAnnotatedComponent component = new RepeatableAnnotatedComponent();

        assertThat(component.capabilityNamespaces())
                .containsExactly("test.repeatable.capability.one", "test.repeatable.capability.two");
        assertThat(component.requirementPolicies())
                .containsExactly(Requirement.Cardinality.MULTIPLE, Requirement.Resolution.OPTIONAL);
        assertThat(component.headerNames()).containsExactly("Bundle-Category", "Bundle-Vendor");
    }

    @Test
    void repeatableContainerAnnotationsRetainOrderedAnnotationValues() {
        Capability firstCapability = new FixedCapability("alpha", "one", "1.0.0", new Class<?>[0], "resolve",
                new String[0]);
        Capability secondCapability = new FixedCapability("beta", "two", "2.0.0", new Class<?>[] {String.class },
                "active", new String[] {"mode=test" });
        Requirement firstRequirement = new FixedRequirement("service", "one", "1.0.0", "", "resolve", new String[0],
                Requirement.Cardinality.SINGLE, Requirement.Resolution.MANDATORY);
        Requirement secondRequirement = new FixedRequirement("bundle", "two", "2.0.0", "(bundle=true)", "active",
                new String[] {"visibility:=reexport" }, Requirement.Cardinality.MULTIPLE,
                Requirement.Resolution.OPTIONAL);
        Header firstHeader = new FixedHeader("Bundle-Category", "integration-test");
        Header secondHeader = new FixedHeader("Bundle-Vendor", "OSGi Alliance");

        Capabilities capabilities = new FixedCapabilities(firstCapability, secondCapability);
        Requirements requirements = new FixedRequirements(firstRequirement, secondRequirement);
        Headers headers = new FixedHeaders(firstHeader, secondHeader);

        assertThat(capabilities.annotationType()).isSameAs(Capabilities.class);
        assertThat(capabilities.value()).containsExactly(firstCapability, secondCapability);
        assertThat(requirements.annotationType()).isSameAs(Requirements.class);
        assertThat(requirements.value()).containsExactly(firstRequirement, secondRequirement);
        assertThat(headers.annotationType()).isSameAs(Headers.class);
        assertThat(headers.value()).containsExactly(firstHeader, secondHeader);
    }

    @Test
    void bundleHeaderAttributeDirectiveExportAndReferenceAnnotationsExposeValues() {
        Header header = new FixedHeader("Bundle-Name", "annotation integration test");
        Attribute attribute = new FixedAttribute("typed-name");
        Directive directive = new FixedDirective("effective");
        Export export = new FixedExport(
                new String[] {"org.example.api", "org.example.spi" },
                new String[] {"mandatory:=version", "status=stable" },
                Export.Substitution.PROVIDER);
        Referenced referenced = new FixedReferenced(ConsumerService.class, ProviderServiceImplementation.class);

        assertThat(header.annotationType()).isSameAs(Header.class);
        assertThat(header.name()).isEqualTo("Bundle-Name");
        assertThat(header.value()).isEqualTo("annotation integration test");
        assertThat(attribute.annotationType()).isSameAs(Attribute.class);
        assertThat(attribute.value()).isEqualTo("typed-name");
        assertThat(directive.annotationType()).isSameAs(Directive.class);
        assertThat(directive.value()).isEqualTo("effective");
        assertThat(export.annotationType()).isSameAs(Export.class);
        assertThat(export.uses()).containsExactly("org.example.api", "org.example.spi");
        assertThat(export.attribute()).containsExactly("mandatory:=version", "status=stable");
        assertThat(export.substitution()).isEqualTo("PROVIDER");
        assertThat(Export.Substitution.CONSUMER).isEqualTo("CONSUMER");
        assertThat(Export.Substitution.NOIMPORT).isEqualTo("NOIMPORT");
        assertThat(Export.Substitution.CALCULATED).isEqualTo("CALCULATED");
        assertThat(referenced.annotationType()).isSameAs(Referenced.class);
        assertThat(referenced.value()).containsExactly(ConsumerService.class, ProviderServiceImplementation.class);
    }

    @Test
    void annotationsCanBeUsedTogetherOnTypesAndMetaAnnotations() {
        AnnotatedComponent component = new AnnotatedComponent();

        assertThat(component.describe()).isEqualTo("annotated-component");
        assertThat(new MetaAnnotatedComponent().name()).isEqualTo("meta-component");
    }

    @Test
    void minimalCapabilityRequirementAndExportDeclarationsUseOptionalDefaults() {
        Capability capability = new FixedCapability("minimal.capability", "", "", new Class<?>[0], "resolve",
                new String[0]);
        Requirement requirement = new FixedRequirement("minimal.requirement", "", "", "", "resolve",
                new String[0], Requirement.Cardinality.SINGLE, Requirement.Resolution.MANDATORY);
        Export export = new FixedExport(new String[0], new String[0], Export.Substitution.CALCULATED);
        Attribute attribute = new FixedAttribute("");
        Directive directive = new FixedDirective("");

        assertThat(capability.namespace()).isEqualTo("minimal.capability");
        assertThat(capability.name()).isEmpty();
        assertThat(capability.version()).isEmpty();
        assertThat(capability.uses()).isEmpty();
        assertThat(capability.effective()).isEqualTo("resolve");
        assertThat(capability.attribute()).isEmpty();
        assertThat(requirement.namespace()).isEqualTo("minimal.requirement");
        assertThat(requirement.name()).isEmpty();
        assertThat(requirement.version()).isEmpty();
        assertThat(requirement.filter()).isEmpty();
        assertThat(requirement.effective()).isEqualTo("resolve");
        assertThat(requirement.attribute()).isEmpty();
        assertThat(requirement.cardinality()).isEqualTo(Requirement.Cardinality.SINGLE);
        assertThat(requirement.resolution()).isEqualTo(Requirement.Resolution.MANDATORY);
        assertThat(export.uses()).isEmpty();
        assertThat(export.attribute()).isEmpty();
        assertThat(export.substitution()).isEqualTo(Export.Substitution.CALCULATED);
        assertThat(attribute.value()).isEmpty();
        assertThat(directive.value()).isEmpty();
    }

    @ConsumerType
    private interface ConsumerService {
        String format(String value);
    }

    private static final class ConsumerServiceImplementation implements ConsumerService {
        @Override
        public String format(String value) {
            return "consumer:" + value;
        }
    }

    @ProviderType
    private interface ProviderService {
        String process(String value);
    }

    @ProviderType
    private static final class ProviderServiceImplementation implements ProviderService {
        @Override
        public String process(String value) {
            return "provider:" + value;
        }
    }

    @Capability(namespace = "test.capability", name = "component", version = "1.0.0", uses = {
            ConsumerService.class, ProviderService.class }, attribute = { "mode=test" })
    @Requirement(namespace = "test.requirement", name = "service", version = "1.0.0", filter = "(service=test)",
            cardinality = Requirement.Cardinality.MULTIPLE, resolution = Requirement.Resolution.OPTIONAL)
    @Header(name = "Bundle-Category", value = "test")
    @Referenced({ String.class, Integer.class })
    private static final class AnnotatedComponent {
        String describe() {
            return "annotated-component";
        }
    }

    @Capability(namespace = "test.meta.capability", name = "meta", version = "1.0.0")
    @Requirement(namespace = "test.meta.requirement", name = "meta", version = "1.0.0")
    private @interface ComponentMetadata {
        @Attribute("component.name")
        String value();

        @Directive("visibility")
        String visibility() default "public";
    }

    @Capability(namespace = "test.repeatable.capability.one", name = "one")
    @Capability(namespace = "test.repeatable.capability.two", name = "two", effective = "active")
    @Requirement(namespace = "test.repeatable.requirement.one", name = "one")
    @Requirement(namespace = "test.repeatable.requirement.two", name = "two",
            cardinality = Requirement.Cardinality.MULTIPLE, resolution = Requirement.Resolution.OPTIONAL)
    @Header(name = "Bundle-Category", value = "integration-test")
    @Header(name = "Bundle-Vendor", value = "OSGi Alliance")
    private static final class RepeatableAnnotatedComponent {
        String[] capabilityNamespaces() {
            return new String[] {"test.repeatable.capability.one", "test.repeatable.capability.two"};
        }

        String[] requirementPolicies() {
            return new String[] {Requirement.Cardinality.MULTIPLE, Requirement.Resolution.OPTIONAL};
        }

        String[] headerNames() {
            return new String[] {"Bundle-Category", "Bundle-Vendor"};
        }
    }

    @Capabilities({
            @Capability(namespace = "test.container.one", name = "one"),
            @Capability(namespace = "test.container.two", name = "two", effective = "active")
    })
    @Requirements({
            @Requirement(namespace = "test.container.requirement.one", name = "one"),
            @Requirement(namespace = "test.container.requirement.two", name = "two",
                    resolution = Requirement.Resolution.OPTIONAL)
    })
    @Headers({
            @Header(name = "Bundle-Description", value = "container annotations"),
            @Header(name = "Bundle-License", value = "CC0")
    })
    @ComponentMetadata("meta-component")
    private static final class MetaAnnotatedComponent {
        String name() {
            return "meta-component";
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
            this.uses = uses;
            this.effective = effective;
            this.attribute = attribute;
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
            return uses;
        }

        @Override
        public String effective() {
            return effective;
        }

        @Override
        public String[] attribute() {
            return attribute;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Capability.class;
        }
    }

    private static final class FixedCapabilities implements Capabilities {
        private final Capability[] value;

        FixedCapabilities(Capability... value) {
            this.value = value;
        }

        @Override
        public Capability[] value() {
            return value;
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
            this.attribute = attribute;
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
            return attribute;
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
            this.value = value;
        }

        @Override
        public Requirement[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Requirements.class;
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
            this.value = value;
        }

        @Override
        public Header[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Headers.class;
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

    private static final class FixedExport implements Export {
        private final String[] uses;
        private final String[] attribute;
        private final String substitution;

        FixedExport(String[] uses, String[] attribute, String substitution) {
            this.uses = uses;
            this.attribute = attribute;
            this.substitution = substitution;
        }

        @Override
        public String[] uses() {
            return uses;
        }

        @Override
        public String[] attribute() {
            return attribute;
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

    private static final class FixedReferenced implements Referenced {
        private final Class<?>[] value;

        FixedReferenced(Class<?>... value) {
            this.value = value;
        }

        @Override
        public Class<?>[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Referenced.class;
        }
    }
}
