/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.Modulithic;
import org.springframework.modulith.core.ApplicationModuleIdentifier;
import org.springframework.modulith.core.ApplicationModuleIdentifiers;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.ModulithMetadata;
import org.springframework.modulith.core.PackageName;
import org.springframework.modulith.core.util.ApplicationModulesExporter;

@Modulithic(
        systemName = "Spring Modulith Core Test",
        useFullyQualifiedModuleNames = true,
        sharedModules = "core",
        additionalPackages = Spring_modulith_coreTest.MODULITH_PACKAGE)
public class Spring_modulith_coreTest {

    static final String MODULITH_PACKAGE = "org.springframework.modulith";
    private static final String CORE_PACKAGE = MODULITH_PACKAGE + ".core";

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
}
