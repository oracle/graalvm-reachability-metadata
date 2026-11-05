/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleDirective;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleOpensDirective;
import com.github.javaparser.ast.modules.ModuleProvidesDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.github.javaparser.ast.modules.ModuleUsesDirective;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises JavaParser's module-descriptor subsystem to build a dependency and service report for a modular application.
public class ModuleDescriptorAnalysisTest {
    @Test
    public void inventoriesModuleBoundariesAndServices() {
        String source = """
                module com.acme.orders {
                    requires transitive com.acme.catalog;
                    requires static com.acme.metrics;
                    exports com.acme.orders.api to com.acme.web;
                    opens com.acme.orders.model to com.fasterxml.jackson.databind;
                    uses com.acme.orders.spi.OrderCodec;
                    provides com.acme.orders.spi.OrderCodec
                            with com.acme.orders.internal.JsonOrderCodec;
                }
                """;

        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        ModuleDeclaration module = compilationUnit.getModule().orElseThrow();
        ModuleReport report = inspect(module);

        assertEquals("com.acme.orders", report.name());
        assertFalse(report.open());
        assertEquals(
                List.of(
                        new Requirement("com.acme.catalog", true, false),
                        new Requirement("com.acme.metrics", false, true)),
                report.requirements());
        assertEquals(
                List.of(new QualifiedBoundary(
                        "com.acme.orders.api", List.of("com.acme.web"))),
                report.exports());
        assertEquals(
                List.of(new QualifiedBoundary(
                        "com.acme.orders.model", List.of("com.fasterxml.jackson.databind"))),
                report.opens());
        assertEquals(List.of("com.acme.orders.spi.OrderCodec"), report.servicesUsed());
        assertEquals(
                List.of(new ServiceProvider(
                        "com.acme.orders.spi.OrderCodec",
                        List.of("com.acme.orders.internal.JsonOrderCodec"))),
                report.servicesProvided());
    }

    private ModuleReport inspect(ModuleDeclaration module) {
        List<Requirement> requirements = module.getDirectives().stream()
                .filter(ModuleRequiresDirective.class::isInstance)
                .map(ModuleRequiresDirective.class::cast)
                .map(requirement -> new Requirement(
                        requirement.getNameAsString(),
                        requirement.isTransitive(),
                        requirement.isStatic()))
                .collect(Collectors.toList());
        List<QualifiedBoundary> exports = boundaries(module, ModuleExportsDirective.class);
        List<QualifiedBoundary> opens = boundaries(module, ModuleOpensDirective.class);
        List<String> servicesUsed = module.getDirectives().stream()
                .filter(ModuleUsesDirective.class::isInstance)
                .map(ModuleUsesDirective.class::cast)
                .map(ModuleUsesDirective::getNameAsString)
                .collect(Collectors.toList());
        List<ServiceProvider> servicesProvided = module.getDirectives().stream()
                .filter(ModuleProvidesDirective.class::isInstance)
                .map(ModuleProvidesDirective.class::cast)
                .map(service -> new ServiceProvider(
                        service.getNameAsString(),
                        service.getWith().stream()
                                .map(name -> name.asString())
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
        return new ModuleReport(
                module.getNameAsString(),
                module.isOpen(),
                requirements,
                exports,
                opens,
                servicesUsed,
                servicesProvided);
    }

    private <T extends ModuleDirective> List<QualifiedBoundary> boundaries(
            ModuleDeclaration module, Class<T> directiveType) {
        return module.getDirectives().stream()
                .filter(directiveType::isInstance)
                .map(directiveType::cast)
                .map(directive -> {
                    if (directive instanceof ModuleExportsDirective exports) {
                        return new QualifiedBoundary(
                                exports.getNameAsString(),
                                exports.getModuleNames().stream()
                                        .map(name -> name.asString())
                                        .collect(Collectors.toList()));
                    }
                    ModuleOpensDirective opens = (ModuleOpensDirective) directive;
                    return new QualifiedBoundary(
                            opens.getNameAsString(),
                            opens.getModuleNames().stream()
                                    .map(name -> name.asString())
                                    .collect(Collectors.toList()));
                })
                .collect(Collectors.toList());
    }

    private record ModuleReport(
            String name,
            boolean open,
            List<Requirement> requirements,
            List<QualifiedBoundary> exports,
            List<QualifiedBoundary> opens,
            List<String> servicesUsed,
            List<ServiceProvider> servicesProvided) {
    }

    private record Requirement(String module, boolean transitive, boolean staticRequirement) {
    }

    private record QualifiedBoundary(String packageName, List<String> targetModules) {
    }

    private record ServiceProvider(String service, List<String> implementations) {
    }
}
