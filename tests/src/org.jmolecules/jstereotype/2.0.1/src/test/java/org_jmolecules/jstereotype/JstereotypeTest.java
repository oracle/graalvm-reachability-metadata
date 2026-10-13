/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jmolecules.jstereotype;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import org.jmolecules.stereotype.Stereotype;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JstereotypeTest {
    @Test
    void configuredStereotypeRetainsEveryMemberAtRuntime() {
        Stereotype stereotype = requireAnnotation(OrderAggregate.class, Stereotype.class);

        assertThat(stereotype.value()).isEqualTo("Order");
        assertThat(stereotype.name()).isEqualTo("Order aggregate");
        assertThat(stereotype.id()).isEqualTo("sales.order");
        assertThat(stereotype.groups()).containsExactly("domain-driven-design", "sales");
        assertThat(stereotype.priority()).isEqualTo(10);
        assertThat(stereotype.inherited()).isFalse();
        assertThat(stereotype.annotationType()).isSameAs(Stereotype.class);
    }

    @Test
    void defaultStereotypeExposesItsDocumentedDefaults() {
        Stereotype stereotype = requireAnnotation(DefaultStereotype.class, Stereotype.class);

        assertThat(stereotype.value()).isEmpty();
        assertThat(stereotype.name()).isEmpty();
        assertThat(stereotype.id()).isEmpty();
        assertThat(stereotype.groups()).isEmpty();
        assertThat(stereotype.priority()).isZero();
        assertThat(stereotype.inherited()).isTrue();
    }

    @Test
    void stereotypeCanDescribeAReusableDomainAnnotation() {
        Stereotype stereotype = requireAnnotation(DomainService.class, Stereotype.class);
        DomainService domainService = requireAnnotation(PricingService.class, DomainService.class);

        assertThat(stereotype.value()).isEmpty();
        assertThat(stereotype.name()).isEqualTo("Domain service");
        assertThat(stereotype.id()).isEqualTo("ddd.domain-service");
        assertThat(stereotype.groups()).containsExactly("domain-driven-design", "service");
        assertThat(stereotype.priority()).isEqualTo(20);
        assertThat(stereotype.inherited()).isTrue();
        assertThat(domainService.annotationType()).isSameAs(DomainService.class);
    }

    @Test
    void stereotypeDeclaresItsRuntimeUsageContract() {
        Retention retention = requireAnnotation(Stereotype.class, Retention.class);
        Target target = requireAnnotation(Stereotype.class, Target.class);

        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target.value()).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.ANNOTATION_TYPE);
        assertThat(Stereotype.class.isAnnotationPresent(Documented.class)).isTrue();
    }

    @Test
    void schemaCatalogAdvertisesBothConfigurationDocuments() throws IOException {
        String catalog = readLibraryResource("jmolecules-catalog.json");

        assertThat(catalog)
                .contains("https://json.schemastore.org/schema-catalog.json")
                .contains("jmolecules-stereotype-groups.json")
                .contains("https://schemas.jmolecules.org/jmolecules-stereotype-groups.schema.json")
                .contains("jmolecules-stereotypes.json")
                .contains("https://schemas.jmolecules.org/jmolecules-stereotypes.schema.json");
    }

    @Test
    void bundledSchemasDescribeStereotypesAndTheirGroups() throws IOException {
        String groupSchema = readLibraryResource("jmolecules-stereotype-groups.schema.json");
        String stereotypeSchema = readLibraryResource("jmolecules-stereotypes.schema.json");

        assertThat(groupSchema)
                .contains("Configuration for jMolecules stereotype groups")
                .contains("\"required\": [\"groups\"]")
                .contains("\"displayName\"")
                .contains("\"architecture\", \"design\", \"technical\"");
        assertThat(stereotypeSchema)
                .contains("Configuration for jMolecules stereotypes")
                .contains("\"required\": [\"stereotypes\"]")
                .contains("\"required\": [\"assignments\", \"groups\"]")
                .contains("jmolecules-stereotype-groups.schema.json#/properties/groups");
    }

    private static String readLibraryResource(String name) throws IOException {
        try (InputStream input = Stereotype.class.getResourceAsStream("/META-INF/" + name)) {
            assertThat(input).as("library resource %s", name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static <A extends Annotation> A requireAnnotation(Class<?> element, Class<A> annotationType) {
        A annotation = element.getAnnotation(annotationType);
        assertThat(annotation).as("%s annotation on %s", annotationType.getSimpleName(), element.getName()).isNotNull();
        return annotation;
    }

    @Stereotype(
            value = "Order",
            name = "Order aggregate",
            id = "sales.order",
            groups = {"domain-driven-design", "sales"},
            priority = 10,
            inherited = false)
    private static final class OrderAggregate {
    }

    @Stereotype
    private static final class DefaultStereotype {
    }

    @Stereotype(
            name = "Domain service",
            id = "ddd.domain-service",
            groups = {"domain-driven-design", "service"},
            priority = 20)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    private @interface DomainService {
    }

    @DomainService
    private static final class PricingService {
    }
}
