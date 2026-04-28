/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_annotation_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.osgi.annotation.bundle.Attribute;
import org.osgi.annotation.bundle.Capabilities;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Directive;
import org.osgi.annotation.bundle.Export;
import org.osgi.annotation.bundle.Export.Substitution;
import org.osgi.annotation.bundle.Header;
import org.osgi.annotation.bundle.Headers;
import org.osgi.annotation.bundle.Referenced;
import org.osgi.annotation.bundle.Requirement;
import org.osgi.annotation.bundle.Requirement.Cardinality;
import org.osgi.annotation.bundle.Requirement.Resolution;
import org.osgi.annotation.bundle.Requirements;

public class Org_osgi_annotation_bundleTest {
    @Test
    void requirementModelsNamespaceFiltersAttributesAndResolutionPolicy() {
        Requirement requirement = new BundleRequirement(
                "osgi.extender",
                "osgi.component",
                "1.5.0",
                "(&(osgi.extender=osgi.component)(version>=1.5.0))",
                "active",
                new String[] {"implementation:String=component", "priority:Long=10"},
                Cardinality.MULTIPLE,
                Resolution.OPTIONAL);

        assertThat(requirement.annotationType()).isSameAs(Requirement.class);
        assertThat(requirement.namespace()).isEqualTo("osgi.extender");
        assertThat(requirement.name()).isEqualTo("osgi.component");
        assertThat(requirement.version()).isEqualTo("1.5.0");
        assertThat(requirement.filter()).isEqualTo("(&(osgi.extender=osgi.component)(version>=1.5.0))");
        assertThat(requirement.effective()).isEqualTo("active");
        assertThat(requirement.attribute()).containsExactly("implementation:String=component", "priority:Long=10");
        assertThat(requirement.cardinality()).isEqualTo("MULTIPLE");
        assertThat(requirement.resolution()).isEqualTo("OPTIONAL");
    }

    @Test
    void requirementConstantsExposeOsgiNamespacePolicyValues() {
        assertThat(Cardinality.SINGLE).isEqualTo("SINGLE");
        assertThat(Cardinality.MULTIPLE).isEqualTo("MULTIPLE");
        assertThat(Resolution.MANDATORY).isEqualTo("MANDATORY");
        assertThat(Resolution.OPTIONAL).isEqualTo("OPTIONAL");
    }

    @Test
    void capabilityModelsProvidedNamespaceUsesAndCustomAttributes() {
        Capability capability = new BundleCapability(
                "osgi.service",
                "com.acme.telemetry.MetricsService",
                "2.1.3",
                new Class<?>[] {MetricsService.class, MetricsListener.class},
                "resolve",
                new String[] {"objectClass:List<String>=com.acme.telemetry.MetricsService", "ranking:Long=100"});

        assertThat(capability.annotationType()).isSameAs(Capability.class);
        assertThat(capability.namespace()).isEqualTo("osgi.service");
        assertThat(capability.name()).isEqualTo("com.acme.telemetry.MetricsService");
        assertThat(capability.version()).isEqualTo("2.1.3");
        assertThat(capability.uses()).containsExactly(MetricsService.class, MetricsListener.class);
        assertThat(capability.effective()).isEqualTo("resolve");
        assertThat(capability.attribute())
                .containsExactly("objectClass:List<String>=com.acme.telemetry.MetricsService", "ranking:Long=100");
    }

    @Test
    void repeatableContainersPreserveDeclaredAnnotationOrder() {
        Requirement firstRequirement = new BundleRequirement("osgi.contract", "JavaServlet", "5.0", "", "resolve",
                new String[] {}, Cardinality.SINGLE, Resolution.MANDATORY);
        Requirement secondRequirement = new BundleRequirement("osgi.implementation", "osgi.http", "1.1", "", "resolve",
                new String[] {"vendor=Acme"}, Cardinality.MULTIPLE, Resolution.OPTIONAL);
        Capability firstCapability = new BundleCapability(
                "osgi.contract", "Metrics", "1.0", new Class<?>[] {}, "resolve", new String[] {});
        Capability secondCapability = new BundleCapability("osgi.service", "MetricsService", "2.0",
                new Class<?>[] {MetricsService.class}, "resolve", new String[] {"scope=singleton"});

        Requirements requirements = new BundleRequirements(firstRequirement, secondRequirement);
        Capabilities capabilities = new BundleCapabilities(firstCapability, secondCapability);

        assertThat(requirements.annotationType()).isSameAs(Requirements.class);
        assertThat(requirements.value()).containsExactly(firstRequirement, secondRequirement);
        assertThat(capabilities.annotationType()).isSameAs(Capabilities.class);
        assertThat(capabilities.value()).containsExactly(firstCapability, secondCapability);
    }

    @Test
    void repeatedAnnotationsCanBeDeclaredDirectlyOnOneApplicationType() {
        MetricsService component = new MultiClauseComponent();

        assertThat(component.record("connections", 3)).isEqualTo("connections=3:multi");
        assertThat(component.listener().name()).isEqualTo("multi-listener");
    }

    @Test
    void headersDescribeManifestMetadataAndRepeatableHeaderContainer() {
        Header symbolicName = new BundleHeader("Bundle-SymbolicName", "com.acme.telemetry;singleton:=true");
        Header activator = new BundleHeader("Bundle-Activator", "com.acme.telemetry.internal.Activator");
        Headers headers = new BundleHeaders(symbolicName, activator);

        assertThat(symbolicName.annotationType()).isSameAs(Header.class);
        assertThat(symbolicName.name()).isEqualTo("Bundle-SymbolicName");
        assertThat(symbolicName.value()).isEqualTo("com.acme.telemetry;singleton:=true");
        assertThat(activator.name()).isEqualTo("Bundle-Activator");
        assertThat(headers.annotationType()).isSameAs(Headers.class);
        assertThat(headers.value()).containsExactly(symbolicName, activator);
    }

    @Test
    void exportModelsPackageUsesAttributesAndSubstitutionPolicies() {
        Export export = new BundleExport(
                new String[] {"com.acme.telemetry.spi", "org.osgi.framework"},
                new String[] {"mandatory:=version", "status=stable"},
                Substitution.PROVIDER);

        assertThat(export.annotationType()).isSameAs(Export.class);
        assertThat(export.uses()).containsExactly("com.acme.telemetry.spi", "org.osgi.framework");
        assertThat(export.attribute()).containsExactly("mandatory:=version", "status=stable");
        assertThat(export.substitution()).isEqualTo("PROVIDER");
        assertThat(Arrays.asList(
                Substitution.CONSUMER,
                Substitution.PROVIDER,
                Substitution.NOIMPORT,
                Substitution.CALCULATED))
                .containsExactly("CONSUMER", "PROVIDER", "NOIMPORT", "CALCULATED");
    }

    @Test
    void attributeAndDirectiveAnnotationsModelManifestClauseParts() {
        Attribute typedAttribute = new BundleAttribute("service.ranking:Long=100");
        Directive directive = new BundleDirective("cardinality:=multiple");
        AnnotatedClause clause = new AnnotatedClause();

        assertThat(typedAttribute.annotationType()).isSameAs(Attribute.class);
        assertThat(typedAttribute.value()).isEqualTo("service.ranking:Long=100");
        assertThat(directive.annotationType()).isSameAs(Directive.class);
        assertThat(directive.value()).isEqualTo("cardinality:=multiple");
        assertThat(clause.describe()).contains("service.ranking", "cardinality");
    }

    @Test
    void referencedAnnotationCarriesAdditionalTypesForAnalysisTools() {
        Referenced referenced = new BundleReferenced(
                MetricsService.class,
                MetricsListener.class,
                AnnotatedComponent.class);

        assertThat(referenced.annotationType()).isSameAs(Referenced.class);
        assertThat(referenced.value()).containsExactly(
                MetricsService.class,
                MetricsListener.class,
                AnnotatedComponent.class);
    }

    @Test
    void attributeAndDirectiveSupportTypeSafeComposedCapabilityAnnotations() {
        MetricsExporter exporter = new AnnotatedMetricsExporter();

        assertThat(exporter.export("latency", 7)).isEqualTo("latency:7:otlp");
        assertThat(exporter.listener().name()).isEqualTo("export-listener");
    }

    @Test
    void classLevelAnnotationsCanBeUsedOnRegularApplicationTypes() {
        MetricsService component = new AnnotatedComponent();

        assertThat(component.record("requests", 42)).isEqualTo("requests=42");
        assertThat(component.listener().name()).isEqualTo("default-listener");
    }

    @Header(name = "Bundle-Name", value = "Telemetry test bundle")
    @Capability(namespace = "osgi.service", name = "com.acme.telemetry.MetricsService", uses = {MetricsService.class})
    @Requirement(namespace = "osgi.extender", name = "osgi.component", resolution = Resolution.OPTIONAL)
    @Referenced({MetricsListener.class})
    private static final class AnnotatedComponent implements MetricsService {
        @Override
        public String record(String metric, long value) {
            return metric + "=" + value;
        }

        @Override
        public MetricsListener listener() {
            return () -> "default-listener";
        }
    }

    @Header(name = "Bundle-Category", value = "monitoring")
    @Header(name = "Bundle-ContactAddress", value = "ops@example.invalid")
    @Capability(namespace = "osgi.service", name = "com.acme.telemetry.MetricsService")
    @Capability(namespace = "osgi.implementation", name = "com.acme.telemetry.runtime", version = "1.0.0")
    @Requirement(namespace = "osgi.extender", name = "osgi.component", resolution = Resolution.OPTIONAL)
    @Requirement(namespace = "osgi.contract", name = "JavaServlet", version = "5.0.0")
    private static final class MultiClauseComponent implements MetricsService {
        @Override
        public String record(String metric, long value) {
            return metric + "=" + value + ":multi";
        }

        @Override
        public MetricsListener listener() {
            return () -> "multi-listener";
        }
    }

    private interface MetricsService {
        String record(String metric, long value);

        MetricsListener listener();
    }

    private interface MetricsListener {
        String name();
    }

    private interface MetricsExporter {
        String export(String metric, long value);

        MetricsListener listener();
    }

    @Capability(namespace = "osgi.service", name = "com.acme.telemetry.MetricsExporter")
    @Requirement(namespace = "osgi.extender", name = "osgi.component", resolution = Resolution.OPTIONAL)
    private @interface TelemetryExporterCapability {
        @Attribute("service.ranking:Long")
        int serviceRanking() default 250;

        @Attribute("vendor")
        String vendor() default "Acme";

        @Directive("effective")
        String effective() default "active";
    }

    @TelemetryExporterCapability(serviceRanking = 500, vendor = "Telemetry Labs")
    private static final class AnnotatedMetricsExporter implements MetricsExporter {
        @Override
        public String export(String metric, long value) {
            return metric + ":" + value + ":otlp";
        }

        @Override
        public MetricsListener listener() {
            return () -> "export-listener";
        }
    }

    private static final class AnnotatedClause {
        @Attribute("service.ranking:Long=100")
        String attribute() {
            return "service.ranking";
        }

        @Directive("cardinality:=multiple")
        String directive() {
            return "cardinality";
        }

        String describe() {
            return attribute() + ";" + directive();
        }
    }

    private static final class BundleRequirement implements Requirement {
        private final String namespace;
        private final String name;
        private final String version;
        private final String filter;
        private final String effective;
        private final String[] attribute;
        private final String cardinality;
        private final String resolution;

        BundleRequirement(
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

    private static final class BundleCapability implements Capability {
        private final String namespace;
        private final String name;
        private final String version;
        private final Class<?>[] uses;
        private final String effective;
        private final String[] attribute;

        BundleCapability(
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

    private static final class BundleRequirements implements Requirements {
        private final Requirement[] value;

        BundleRequirements(Requirement... value) {
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

    private static final class BundleCapabilities implements Capabilities {
        private final Capability[] value;

        BundleCapabilities(Capability... value) {
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

    private static final class BundleHeader implements Header {
        private final String name;
        private final String value;

        BundleHeader(String name, String value) {
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

    private static final class BundleHeaders implements Headers {
        private final Header[] value;

        BundleHeaders(Header... value) {
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

    private static final class BundleExport implements Export {
        private final String[] uses;
        private final String[] attribute;
        private final String substitution;

        BundleExport(String[] uses, String[] attribute, String substitution) {
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

    private static final class BundleAttribute implements Attribute {
        private final String value;

        BundleAttribute(String value) {
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

    private static final class BundleDirective implements Directive {
        private final String value;

        BundleDirective(String value) {
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

    private static final class BundleReferenced implements Referenced {
        private final Class<?>[] value;

        BundleReferenced(Class<?>... value) {
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
