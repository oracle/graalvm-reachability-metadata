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
import org.osgi.annotation.bundle.Requirement;
import org.osgi.annotation.bundle.Requirements;
import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.annotation.versioning.Version;

public class Osgi_annotationTest {
    @Test
    void capabilityAnnotationContractSupportsDirectAndRepeatableDefinitions() {
        Capability extender = new CapabilitySpec(
                "osgi.extender",
                "example.extender",
                "1.2.3",
                new Class<?>[] {AnnotatedBundle.class},
                "active",
                new String[] {"kind=integration", "ranking:Long=10"});
        Capability contract = new CapabilitySpec("osgi.contract", "example.contract");
        Capabilities capabilities = new CapabilitiesSpec(extender, contract);

        assertThat(extender.annotationType()).isSameAs(Capability.class);
        assertThat(extender.namespace()).isEqualTo("osgi.extender");
        assertThat(extender.name()).isEqualTo("example.extender");
        assertThat(extender.version()).isEqualTo("1.2.3");
        assertThat(extender.uses()).containsExactly(AnnotatedBundle.class);
        assertThat(extender.effective()).isEqualTo("active");
        assertThat(extender.attribute()).containsExactly("kind=integration", "ranking:Long=10");
        assertThat(capabilities.annotationType()).isSameAs(Capabilities.class);
        assertThat(capabilities.value()).containsExactly(extender, contract);
    }

    @Test
    void requirementAnnotationContractSupportsFiltersAndResolverDirectives() {
        Requirement requirement = new RequirementSpec(
                "osgi.service",
                "example.service",
                "2.0.0",
                "(component.name=example)",
                "resolve",
                new String[] {"objectClass:List<String>=org.example.Service"},
                Requirement.Cardinality.MULTIPLE,
                Requirement.Resolution.OPTIONAL);
        Requirements requirements = new RequirementsSpec(requirement);

        assertThat(requirement.annotationType()).isSameAs(Requirement.class);
        assertThat(requirement.namespace()).isEqualTo("osgi.service");
        assertThat(requirement.name()).isEqualTo("example.service");
        assertThat(requirement.version()).isEqualTo("2.0.0");
        assertThat(requirement.filter()).isEqualTo("(component.name=example)");
        assertThat(requirement.effective()).isEqualTo("resolve");
        assertThat(requirement.attribute()).containsExactly("objectClass:List<String>=org.example.Service");
        assertThat(requirement.cardinality()).isEqualTo(Requirement.Cardinality.MULTIPLE);
        assertThat(requirement.resolution()).isEqualTo(Requirement.Resolution.OPTIONAL);
        assertThat(requirements.annotationType()).isSameAs(Requirements.class);
        assertThat(requirements.value()).containsExactly(requirement);
    }

    @Test
    void bundleHeaderAnnotationsExposeNamesValuesAndContainers() {
        Header category = new HeaderSpec("Bundle-Category", "osgi,test");
        Header contact = new HeaderSpec("Bundle-ContactAddress", "https://www.osgi.org/");
        Headers headers = new HeadersSpec(category, contact);

        assertThat(category.annotationType()).isSameAs(Header.class);
        assertThat(category.name()).isEqualTo("Bundle-Category");
        assertThat(category.value()).isEqualTo("osgi,test");
        assertThat(headers.annotationType()).isSameAs(Headers.class);
        assertThat(headers.value()).containsExactly(category, contact);
    }

    @Test
    void packageExportAnnotationExposesUsesAttributesAndSubstitutionPolicy() {
        Export export = new ExportSpec(
                new String[] {"java.time", "java.util"},
                new String[] {"mandatory:=version", "status=stable"},
                Export.Substitution.PROVIDER);

        assertThat(export.annotationType()).isSameAs(Export.class);
        assertThat(export.uses()).containsExactly("java.time", "java.util");
        assertThat(export.attribute()).containsExactly("mandatory:=version", "status=stable");
        assertThat(export.substitution()).isEqualTo(Export.Substitution.PROVIDER);
    }

    @Test
    void metaAnnotationElementMarkersExposeAttributeAndDirectiveNames() {
        Attribute attribute = new AttributeSpec("contract.version");
        Directive directive = new DirectiveSpec("effective");

        assertThat(attribute.annotationType()).isSameAs(Attribute.class);
        assertThat(attribute.value()).isEqualTo("contract.version");
        assertThat(directive.annotationType()).isSameAs(Directive.class);
        assertThat(directive.value()).isEqualTo("effective");
    }

    @Test
    void versioningAnnotationsExposePackageVersionsAndRoleMarkers() {
        Version version = new VersionSpec("7.0.0.osgi");
        Annotation consumerType = new ConsumerTypeSpec();
        Annotation providerType = new ProviderTypeSpec();

        assertThat(version.annotationType()).isSameAs(Version.class);
        assertThat(version.value()).isEqualTo("7.0.0.osgi");
        assertThat(consumerType.annotationType()).isSameAs(ConsumerType.class);
        assertThat(providerType.annotationType()).isSameAs(ProviderType.class);
    }

    @Test
    void stringConstantsExposeManifestDirectiveValues() {
        assertThat(Requirement.Cardinality.SINGLE).isEqualTo("SINGLE");
        assertThat(Requirement.Cardinality.MULTIPLE).isEqualTo("MULTIPLE");
        assertThat(Requirement.Resolution.MANDATORY).isEqualTo("MANDATORY");
        assertThat(Requirement.Resolution.OPTIONAL).isEqualTo("OPTIONAL");
        assertThat(Export.Substitution.CONSUMER).isEqualTo("CONSUMER");
        assertThat(Export.Substitution.PROVIDER).isEqualTo("PROVIDER");
        assertThat(Export.Substitution.NOIMPORT).isEqualTo("NOIMPORT");
        assertThat(Export.Substitution.CALCULATED).isEqualTo("CALCULATED");
    }

    @Test
    void annotatedFixturesRemainUsableWithoutRuntimeAnnotationProcessing() {
        AnnotatedBundle bundle = new AnnotatedBundle();
        ConsumerService consumerService = new ConsumerServiceImpl();
        ProviderService providerService = new ProviderServiceImpl();

        assertThat(bundle.describe()).isEqualTo("annotated-bundle");
        assertThat(consumerService.consume("request")).isEqualTo("consumed:request");
        assertThat(providerService.provide()).isEqualTo("provided");
    }

    @Test
    void stringConstantsCanBeSelectedByPublicNamesForManifestConfiguration() {
        String cardinality = Requirement.Cardinality.MULTIPLE;
        String resolution = Requirement.Resolution.OPTIONAL;
        String substitution = Export.Substitution.NOIMPORT;

        assertThat(cardinality).isEqualTo("MULTIPLE");
        assertThat(resolution).isEqualTo("OPTIONAL");
        assertThat(substitution).isEqualTo("NOIMPORT");
    }

    @Test
    void repeatedRequirementAnnotationsSupportMultipleTypeLevelRequirements() {
        MultiRequirementBundle bundle = new MultiRequirementBundle();

        assertThat(bundle.describe()).isEqualTo("multi-requirement-bundle");
    }

    @Capability(namespace = "example.capability", name = "typed", version = "1.0.0")
    @Requirement(namespace = "example.requirement", name = "typed", resolution = Requirement.Resolution.OPTIONAL)
    private @interface BundleContract {
        @Attribute("contract.name")
        String name() default "example";

        @Directive("visibility")
        String visibility() default "private";
    }

    @BundleContract(name = "integration", visibility = "public")
    @Capability(namespace = "osgi.extender", name = "sample.extender", version = "1.0.0")
    @Capability(namespace = "osgi.contract", name = "sample.contract", version = "2.0.0")
    @Requirement(namespace = "osgi.service", name = "sample.service", cardinality = Requirement.Cardinality.MULTIPLE)
    @Header(name = "Bundle-Category", value = "test")
    @Header(name = "Bundle-Description", value = "OSGi annotation integration fixture")
    private static final class AnnotatedBundle {
        String describe() {
            return "annotated-bundle";
        }
    }

    @Requirement(namespace = "osgi.service", name = "sample.primary")
    @Requirement(namespace = "osgi.ee", filter = "(&(osgi.ee=JavaSE)(version>=11))")
    private static final class MultiRequirementBundle {
        String describe() {
            return "multi-requirement-bundle";
        }
    }

    @ConsumerType
    private interface ConsumerService {
        String consume(String value);
    }

    private static final class ConsumerServiceImpl implements ConsumerService {
        @Override
        public String consume(String value) {
            return "consumed:" + value;
        }
    }

    @ProviderType
    private interface ProviderService {
        String provide();
    }

    private static final class ProviderServiceImpl implements ProviderService {
        @Override
        public String provide() {
            return "provided";
        }
    }

    private static final class CapabilitySpec implements Capability {
        private final String namespace;
        private final String name;
        private final String version;
        private final Class<?>[] uses;
        private final String effective;
        private final String[] attribute;

        CapabilitySpec(String namespace, String name) {
            this(namespace, name, "", new Class<?>[0], "resolve", new String[0]);
        }

        CapabilitySpec(
                String namespace,
                String name,
                String version,
                Class<?>[] uses,
                String effective,
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

    private static final class CapabilitiesSpec implements Capabilities {
        private final Capability[] value;

        CapabilitiesSpec(Capability... value) {
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

    private static final class RequirementSpec implements Requirement {
        private final String namespace;
        private final String name;
        private final String version;
        private final String filter;
        private final String effective;
        private final String[] attribute;
        private final String cardinality;
        private final String resolution;

        RequirementSpec(
                String namespace,
                String name,
                String version,
                String filter,
                String effective,
                String[] attribute,
                String cardinality,
                String resolution) {
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

    private static final class RequirementsSpec implements Requirements {
        private final Requirement[] value;

        RequirementsSpec(Requirement... value) {
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

    private static final class HeaderSpec implements Header {
        private final String name;
        private final String value;

        HeaderSpec(String name, String value) {
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

    private static final class HeadersSpec implements Headers {
        private final Header[] value;

        HeadersSpec(Header... value) {
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

    private static final class ExportSpec implements Export {
        private final String[] uses;
        private final String[] attribute;
        private final String substitution;

        ExportSpec(String[] uses, String[] attribute, String substitution) {
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

    private static final class AttributeSpec implements Attribute {
        private final String value;

        AttributeSpec(String value) {
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

    private static final class DirectiveSpec implements Directive {
        private final String value;

        DirectiveSpec(String value) {
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

    private static final class VersionSpec implements Version {
        private final String value;

        VersionSpec(String value) {
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

    private static final class ConsumerTypeSpec implements ConsumerType {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ConsumerType.class;
        }
    }

    private static final class ProviderTypeSpec implements ProviderType {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ProviderType.class;
        }
    }
}
