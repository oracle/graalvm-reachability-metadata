/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_component_annotations;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Configuration;
import org.codehaus.plexus.component.annotations.Requirement;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:annotationAccess")
public class Plexus_component_annotationsTest {
    @Test
    void componentAnnotationExposesAllConfiguredValuesAndIsInherited() {
        Component component = FullyConfiguredComponent.class.getAnnotation(Component.class);

        assertThat(component.annotationType()).isSameAs(Component.class);
        assertThat(component.role()).isSameAs(ServiceRole.class);
        assertThat(component.hint()).isEqualTo("primary");
        assertThat(component.version()).isEqualTo("1.0");
        assertThat(component.alias()).isEqualTo("service-alias");
        assertThat(component.description()).isEqualTo("fully configured test component");
        assertThat(component.lifecycleHandler()).isEqualTo("singleton");
        assertThat(component.instantiationStrategy()).isEqualTo("per-lookup");
        assertThat(component.factory()).isEqualTo("factory-id");
        assertThat(component.type()).isEqualTo("service-type");
        assertThat(component.profile()).isEqualTo("integration");
        assertThat(component.composer()).isEqualTo("field-composer");
        assertThat(component.configurator()).isEqualTo("basic-configurator");
        assertThat(component.isolatedRealm()).isTrue();

        Component inheritedComponent = DerivedComponent.class.getAnnotation(Component.class);
        assertThat(inheritedComponent).isEqualTo(component);
    }

    @Test
    void componentAnnotationCanDescribeInterfaceComponents() {
        Component component = InterfaceComponent.class.getAnnotation(Component.class);

        assertThat(component.role()).isSameAs(InterfaceComponent.class);
        assertThat(component.hint()).isEqualTo("contract");
        assertThat(component.description()).isEqualTo("component interface contract");
    }

    @Test
    void componentAnnotationUsesDocumentedRuntimeTypeTargetAndExpectedDefaults() {
        assertThat(Component.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Component.class.isAnnotationPresent(Inherited.class)).isTrue();
        assertThat(Component.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Component.class.getAnnotation(Target.class).value()).containsExactly(ElementType.TYPE);

        Component component = MinimalComponent.class.getAnnotation(Component.class);
        assertThat(component.role()).isSameAs(ServiceRole.class);
        assertThat(component.hint()).isEmpty();
        assertThat(component.version()).isEmpty();
        assertThat(component.alias()).isEmpty();
        assertThat(component.description()).isEmpty();
        assertThat(component.lifecycleHandler()).isEmpty();
        assertThat(component.instantiationStrategy()).isEmpty();
        assertThat(component.factory()).isEmpty();
        assertThat(component.type()).isEmpty();
        assertThat(component.profile()).isEmpty();
        assertThat(component.composer()).isEmpty();
        assertThat(component.configurator()).isEmpty();
        assertThat(component.isolatedRealm()).isFalse();
    }

    @Test
    void requirementAnnotationSupportsFieldsMethodsSingleHintsMultipleHintsAndDefaults() throws Exception {
        Field requiredField = DependencyHolder.class.getDeclaredField("requiredField");
        Requirement fieldRequirement = requiredField.getAnnotation(Requirement.class);
        assertThat(fieldRequirement.annotationType()).isSameAs(Requirement.class);
        assertThat(fieldRequirement.role()).isSameAs(Repository.class);
        assertThat(fieldRequirement.hint()).isEqualTo("central");
        assertThat(fieldRequirement.hints()).isEmpty();
        assertThat(fieldRequirement.optional()).isFalse();

        Method optionalMethod = DependencyHolder.class.getDeclaredMethod("setMirrors", Mirror.class);
        Requirement methodRequirement = optionalMethod.getAnnotation(Requirement.class);
        assertThat(methodRequirement.role()).isSameAs(Mirror.class);
        assertThat(methodRequirement.hint()).isEmpty();
        assertThat(methodRequirement.hints()).containsExactly("primary", "backup");
        assertThat(methodRequirement.optional()).isTrue();

        Field defaultField = DependencyHolder.class.getDeclaredField("defaultedField");
        Requirement defaultRequirement = defaultField.getAnnotation(Requirement.class);
        assertThat(defaultRequirement.role()).isSameAs(Object.class);
        assertThat(defaultRequirement.hint()).isEmpty();
        assertThat(defaultRequirement.hints()).isEmpty();
        assertThat(defaultRequirement.optional()).isFalse();
    }

    @Test
    void requirementAnnotationUsesDocumentedRuntimeFieldAndMethodTargets() {
        assertThat(Requirement.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Requirement.class.isAnnotationPresent(Inherited.class)).isTrue();
        assertThat(Requirement.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Requirement.class.getAnnotation(Target.class).value()).containsExactly(ElementType.FIELD, ElementType.METHOD);
    }

    @Test
    void requirementHintsAreReturnedAsDefensiveCopies() throws Exception {
        Requirement requirement = MultiRepositoryHolder.class.getDeclaredField("repositories").getAnnotation(Requirement.class);

        String[] hints = requirement.hints();
        hints[0] = "mutated";

        assertThat(requirement.hints()).containsExactly("snapshots", "releases");
        assertThat(requirement.hints()).isNotSameAs(hints);
    }

    @Test
    void configurationAnnotationSupportsNamedAndDefaultNamedFieldValues() throws Exception {
        Field namedField = ConfigurationHolder.class.getDeclaredField("namedValue");
        Configuration namedConfiguration = namedField.getAnnotation(Configuration.class);
        assertThat(namedConfiguration.annotationType()).isSameAs(Configuration.class);
        assertThat(namedConfiguration.name()).isEqualTo("endpoint");
        assertThat(namedConfiguration.value()).isEqualTo("https://repo.example.test");

        Field defaultNameField = ConfigurationHolder.class.getDeclaredField("defaultNameValue");
        Configuration defaultNameConfiguration = defaultNameField.getAnnotation(Configuration.class);
        assertThat(defaultNameConfiguration.name()).isEmpty();
        assertThat(defaultNameConfiguration.value()).isEqualTo("enabled");
    }

    @Test
    void configurationAnnotationUsesDocumentedRuntimeFieldTarget() {
        assertThat(Configuration.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(Configuration.class.isAnnotationPresent(Inherited.class)).isTrue();
        assertThat(Configuration.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Configuration.class.getAnnotation(Target.class).value()).containsExactly(ElementType.FIELD);
    }

    interface ServiceRole {
    }

    interface Repository {
    }

    interface Mirror {
    }

    interface PluginRepository {
    }

    @Component(
            role = ServiceRole.class,
            hint = "primary",
            version = "1.0",
            alias = "service-alias",
            description = "fully configured test component",
            lifecycleHandler = "singleton",
            instantiationStrategy = "per-lookup",
            factory = "factory-id",
            type = "service-type",
            profile = "integration",
            composer = "field-composer",
            configurator = "basic-configurator",
            isolatedRealm = true)
    public static class FullyConfiguredComponent {
    }

    @Component(role = InterfaceComponent.class, hint = "contract", description = "component interface contract")
    public interface InterfaceComponent {
    }

    public static class DerivedComponent extends FullyConfiguredComponent {
    }

    @Component(role = ServiceRole.class)
    public static class MinimalComponent {
    }

    public static class DependencyHolder {
        @Requirement(role = Repository.class, hint = "central")
        private Repository requiredField;

        @Requirement
        private Object defaultedField;

        @Requirement(role = Mirror.class, hints = {"primary", "backup"}, optional = true)
        public void setMirrors(Mirror mirror) {
            this.requiredField = null;
        }
    }

    public static class MultiRepositoryHolder {
        @Requirement(role = PluginRepository.class, hints = {"snapshots", "releases"})
        public PluginRepository repositories;
    }

    public static class ConfigurationHolder {
        @Configuration(name = "endpoint", value = "https://repo.example.test")
        private String namedValue;

        @Configuration("enabled")
        private String defaultNameValue;
    }
}
