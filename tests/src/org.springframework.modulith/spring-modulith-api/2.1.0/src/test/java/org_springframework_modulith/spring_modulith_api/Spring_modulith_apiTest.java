/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.ApplicationModule.Type;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.modulith.Modulith;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.NamedInterface;
import org.springframework.modulith.PackageInfo;

public class Spring_modulith_apiTest {

    @Test
    void applicationModuleRetainsConfigurationAndSuppliesClosedDefaults() {
        ApplicationModule configured = AnnotatedElementUtils.findMergedAnnotation(OrdersModule.class, ApplicationModule.class);
        ApplicationModule defaults = AnnotatedElementUtils.findMergedAnnotation(DefaultModule.class, ApplicationModule.class);

        assertThat(configured.id()).isEqualTo("orders");
        assertThat(configured.displayName()).isEqualTo("Order Management");
        assertThat(configured.allowedDependencies()).containsExactly("inventory", "billing::api");
        assertThat(configured.type()).isEqualTo(Type.OPEN);

        assertThat(defaults.id()).isEmpty();
        assertThat(defaults.displayName()).isEmpty();
        assertThat(defaults.allowedDependencies()).containsExactly(ApplicationModule.OPEN_TOKEN);
        assertThat(defaults.type()).isEqualTo(Type.CLOSED);
    }

    @Test
    void namedInterfaceRetainsBothNamesAndPropagationPolicy() {
        NamedInterface byValue = AnnotatedElementUtils.findMergedAnnotation(CustomerInterfaces.class, NamedInterface.class);
        NamedInterface byName = AnnotatedElementUtils.findMergedAnnotation(BillingInterface.class, NamedInterface.class);
        NamedInterface defaults = AnnotatedElementUtils.findMergedAnnotation(DefaultNamedInterface.class, NamedInterface.class);

        assertThat(byValue.value()).containsExactly("api", "events");
        assertThat(byValue.name()).containsExactly("api", "events");
        assertThat(byValue.propagate()).isFalse();
        assertThat(byName.value()).containsExactly("billing");
        assertThat(byName.name()).containsExactly("billing");
        assertThat(byName.propagate()).isTrue();
        assertThat(defaults.value()).isEmpty();
        assertThat(defaults.name()).isEmpty();
        assertThat(defaults.propagate()).isTrue();
    }

    @Test
    void modulithicRetainsApplicationStructureAndSuppliesDefaults() {
        Modulithic configured = AnnotatedElementUtils.findMergedAnnotation(InventoryApplication.class, Modulithic.class);
        Modulithic defaults = AnnotatedElementUtils.findMergedAnnotation(DefaultModulithicApplication.class, Modulithic.class);

        assertThat(configured.systemName()).isEqualTo("Inventory");
        assertThat(configured.useFullyQualifiedModuleNames()).isTrue();
        assertThat(configured.sharedModules()).containsExactly("shared", "security");
        assertThat(configured.additionalPackages()).containsExactly("example.inventory", "example.catalog");

        assertThat(defaults.systemName()).isEmpty();
        assertThat(defaults.useFullyQualifiedModuleNames()).isFalse();
        assertThat(defaults.sharedModules()).isEmpty();
        assertThat(defaults.additionalPackages()).isEmpty();
    }

    @Test
    void modulithRetainsApplicationStructureAndSuppliesDefaults() {
        Modulith configured = AnnotatedElementUtils.findMergedAnnotation(OrderApplication.class, Modulith.class);
        Modulith defaults = AnnotatedElementUtils.findMergedAnnotation(DefaultModulithApplication.class, Modulith.class);

        assertThat(configured.systemName()).isEqualTo("Orders");
        assertThat(configured.useFullyQualifiedModuleNames()).isTrue();
        assertThat(configured.sharedModules()).containsExactly("shared-kernel");
        assertThat(configured.additionalPackages()).containsExactly("example.orders");

        assertThat(defaults.systemName()).isEmpty();
        assertThat(defaults.useFullyQualifiedModuleNames()).isFalse();
        assertThat(defaults.sharedModules()).isEmpty();
        assertThat(defaults.additionalPackages()).isEmpty();
    }

    @Test
    void modulithComposesSpringBootAndModulithicMetadata() {
        Modulithic modulithic = AnnotatedElementUtils.findMergedAnnotation(OrderApplication.class, Modulithic.class);

        assertThat(modulithic).isNotNull();
        assertThat(modulithic.systemName()).isEqualTo("Orders");
        assertThat(modulithic.useFullyQualifiedModuleNames()).isTrue();
        assertThat(modulithic.sharedModules()).containsExactly("shared-kernel");
        assertThat(modulithic.additionalPackages()).containsExactly("example.orders");
        assertThat(AnnotatedElementUtils.hasAnnotation(OrderApplication.class, SpringBootApplication.class)).isTrue();
    }

    @Test
    void applicationModuleInitializerExecutesInitializationCallback() {
        RecordingInitializer initializer = new RecordingInitializer();

        initializer.initialize();
        initializer.initialize();

        assertThat(initializer.invocationCount).isEqualTo(2);
    }

    @Test
    void packageInfoMarksAlternativePackageMetadataTypesAtRuntime() {
        PackageInfo packageInfo = AnnotatedElementUtils.findMergedAnnotation(AlternativePackageMetadata.class, PackageInfo.class);

        assertThat(packageInfo).isNotNull();
        assertThat(packageInfo.annotationType()).isEqualTo(PackageInfo.class);
    }

    @ApplicationModule(
            id = "orders",
            displayName = "Order Management",
            allowedDependencies = {"inventory", "billing::api"},
            type = Type.OPEN)
    public static class OrdersModule {}

    @ApplicationModule
    public static class DefaultModule {}

    @NamedInterface(value = {"api", "events"}, propagate = false)
    public static class CustomerInterfaces {}

    @NamedInterface(name = "billing")
    public static class BillingInterface {}

    @NamedInterface
    public static class DefaultNamedInterface {}

    @Modulithic(
            systemName = "Inventory",
            useFullyQualifiedModuleNames = true,
            sharedModules = {"shared", "security"},
            additionalPackages = {"example.inventory", "example.catalog"})
    public static class InventoryApplication {}

    @Modulithic
    public static class DefaultModulithicApplication {}

    @Modulith(
            systemName = "Orders",
            useFullyQualifiedModuleNames = true,
            sharedModules = "shared-kernel",
            additionalPackages = "example.orders")
    public static class OrderApplication {}

    @Modulith
    public static class DefaultModulithApplication {}

    @PackageInfo
    public static class AlternativePackageMetadata {}

    public static class RecordingInitializer implements ApplicationModuleInitializer {

        private int invocationCount;

        @Override
        public void initialize() {
            invocationCount++;
        }
    }
}
