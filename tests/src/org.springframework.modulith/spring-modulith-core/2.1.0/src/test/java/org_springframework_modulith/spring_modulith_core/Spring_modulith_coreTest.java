/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyDepth;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.JavaPackage;
import org.springframework.modulith.core.ModulithMetadata;
import org.springframework.modulith.core.NamedInterface;
import org.springframework.modulith.core.PackageName;
import org.springframework.modulith.core.VerificationOptions;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.core.util.ApplicationModulesExporter;

import com.tngtech.archunit.core.domain.JavaClass;

@Modulithic(
        systemName = "Spring Modulith Core Test",
        useFullyQualifiedModuleNames = true,
        sharedModules = "core",
        additionalPackages = Spring_modulith_coreTest.MODULITH_PACKAGE)
public class Spring_modulith_coreTest {

    static final String MODULITH_PACKAGE = "org.springframework.modulith";
    private static final String CORE_PACKAGE = MODULITH_PACKAGE + ".core";

    @Test
    void discoversApplicationModulesFromTheClasspath() {
        ApplicationModules modules = applicationModules();
        ApplicationModule core = modules.getModuleByName("core").orElseThrow();

        assertThat(modules.getSource()).isEqualTo(MODULITH_PACKAGE);
        assertThat(modules.getRootPackages())
                .extracting(JavaPackage::getName)
                .containsExactly(MODULITH_PACKAGE);
        assertThat(modules.stream())
                .extracting(module -> module.getIdentifier().toString())
                .containsExactly("core");
        assertThat(core.getDisplayName()).isEqualTo("Core");
        assertThat(core.getBasePackage().getName()).isEqualTo(CORE_PACKAGE);
        assertThat(modules.contains(ApplicationModules.class)).isTrue();
        assertThat(modules.withinRootPackages(Modulithic.class.getName())).isTrue();
        assertThat(modules.getModuleByType(ApplicationModules.class)).contains(core);
        assertThat(modules.getModuleByType(ApplicationModulesExporter.class)).contains(core);
        assertThat(modules.getModuleForPackage(CORE_PACKAGE + ".util")).contains(core);
    }

    @Test
    void modelsModulePackagesInterfacesAndDependencies() {
        ApplicationModules modules = applicationModules();
        ApplicationModule core = modules.getModuleByName("core").orElseThrow();
        JavaPackage corePackage = core.getBasePackage();
        NamedInterface unnamed = core.getNamedInterfaces().getUnnamedInterface();

        assertThat(ApplicationModuleDetectionStrategy.directSubPackage()
                        .getModuleBasePackages(corePackage))
                .extracting(JavaPackage::getName)
                .containsExactly(CORE_PACKAGE + ".config", CORE_PACKAGE + ".util");
        assertThat(ApplicationModuleDetectionStrategy.explicitlyAnnotated()
                        .getModuleBasePackages(corePackage))
                .isEmpty();
        assertThat(core.getNamedInterfaces().hasExplicitInterfaces()).isFalse();
        assertThat(unnamed.isUnnamed()).isTrue();
        assertThat(unnamed.contains(ApplicationModules.class)).isTrue();
        assertThat(core.isExposed(ApplicationModules.class)).isTrue();
        assertThat(core.isExposed(ApplicationModulesExporter.class)).isFalse();
        assertThat(core.getInternalTypes())
                .extracting(JavaClass::getName)
                .contains(ApplicationModulesExporter.class.getName());
        assertThat(core.getType("ApplicationModules"))
                .hasValueSatisfying(type -> assertThat(type.getName()).isEqualTo(ApplicationModules.class.getName()));
        assertThat(core.getDirectDependencies(modules)).matches(dependencies -> dependencies.isEmpty());
        assertThat(core.getAllDependencies(modules)).matches(dependencies -> dependencies.isEmpty());
        assertThat(core.getBootstrapDependencies(modules, DependencyDepth.ALL)).isEmpty();
        assertThat(modules.getNestedModules(core)).isEmpty();
    }

    @Test
    void verifiesTheDiscoveredArchitectureWithAdditionalRules() {
        ApplicationModules modules = applicationModules();
        VerificationOptions options = VerificationOptions.defaults()
                .withoutAdditionalVerifications()
                .andAdditionalVerifications(classes().should().resideInAPackage(MODULITH_PACKAGE + ".."));

        Violations violations = modules.detectViolations(options);

        assertThat(violations.hasViolations()).isFalse();
        assertThat(violations.getMessages()).isEmpty();
        assertThat(modules.verify(options)).isSameAs(modules);
        assertThat(modules.verify(options)).isSameAs(modules);
    }

    @Test
    void exportsSimpleAndDetailedModuleModelsAsJson() {
        ApplicationModulesExporter exporter = new ApplicationModulesExporter(applicationModules());

        String simple = exporter.toJson();
        String detailed = exporter.toFullJson();

        assertThat(simple)
                .contains("\"core\"")
                .contains("\"displayName\" : \"Core\"")
                .contains("\"basePackage\" : \"" + CORE_PACKAGE + "\"")
                .contains("\"type\" : \"closed\"")
                .contains("\"shared\" : false")
                .contains("\"dependencies\" : []")
                .doesNotContain("\"namedInterfaces\"");
        assertThat(detailed)
                .contains("\"namedInterfaces\"")
                .contains("\"<<UNNAMED>>\"")
                .contains(ApplicationModules.class.getName());
    }

    @Test
    void filtersSelectedPackagesDuringModuleAnalysis() {
        ApplicationModules filtered = ApplicationModules.of(
                MODULITH_PACKAGE, ApplicationModules.Filters.withoutModule("util"));
        ApplicationModule core = filtered.getModuleByName("core").orElseThrow();

        assertThat(filtered.contains(ApplicationModules.class)).isTrue();
        assertThat(filtered.contains(ApplicationModulesExporter.class)).isFalse();
        assertThat(core.getType(ApplicationModules.class.getName())).isPresent();
        assertThat(core.getType(ApplicationModulesExporter.class.getName())).isEmpty();
    }

    @Test
    void readsModulithConfigurationFromThePublicAnnotation() {
        ModulithMetadata metadata = ModulithMetadata.of(Spring_modulith_coreTest.class);

        assertThat(metadata.getSource()).isEqualTo(Spring_modulith_coreTest.class);
        assertThat(metadata.getSystemName()).contains("Spring Modulith Core Test");
        assertThat(metadata.useFullyQualifiedModuleNames()).isTrue();
        assertThat(metadata.getBasePackages())
                .containsExactly(Spring_modulith_coreTest.class.getPackageName(), MODULITH_PACKAGE);
        assertThat(metadata.getSharedModuleIdentifiers())
                .extracting(ApplicationModuleIdentifier::toString)
                .containsExactly("core");
    }

    @Test
    void exposesStableIdentifiersPackageNamesAndDependencyTypes() {
        ApplicationModuleIdentifier core = ApplicationModuleIdentifier.of("core");
        ApplicationModuleIdentifier events = ApplicationModuleIdentifier.of("events");
        PackageName corePackage = PackageName.ofType(ApplicationModules.class);
        PackageName utilityPackage = PackageName.ofType(ApplicationModulesExporter.class);
        List<String> identifiers = ApplicationModuleIdentifiers.of(List.of(core, events)).stream()
                .map(ApplicationModuleIdentifier::toString)
                .toList();

        assertThat(core).isEqualTo(ApplicationModuleIdentifier.of("core"));
        assertThat(core).isLessThan(events);
        assertThat(identifiers).containsExactly("core", "events");
        assertThat(corePackage.toString()).isEqualTo(CORE_PACKAGE);
        assertThat(corePackage.contains(utilityPackage)).isTrue();
        assertThat(DependencyType.allBut(DependencyType.DEFAULT))
                .containsExactlyInAnyOrder(
                        DependencyType.USES_COMPONENT,
                        DependencyType.ENTITY,
                        DependencyType.EVENT_LISTENER);
        assertThatThrownBy(() -> ApplicationModuleIdentifier.of("core::api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("double-colon");
    }

    private static ApplicationModules applicationModules() {
        return ApplicationModules.of(MODULITH_PACKAGE);
    }
}
