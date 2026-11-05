/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.utils.SourceRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's source-root subsystem to index application classes from a source tree.
public class SourceRootCatalogTest {
    @Test
    public void buildsAnApiCatalogFromAProjectSourceTree(@TempDir Path sourceRoot) throws IOException {
        Path packageDirectory = sourceRoot.resolve("com/acme/orders");
        Files.createDirectories(packageDirectory);
        Files.writeString(
                packageDirectory.resolve("Order.java"),
                """
                        package com.acme.orders;

                        public record Order(String id, int itemCount) {
                        }
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                packageDirectory.resolve("OrderService.java"),
                """
                        package com.acme.orders;

                        public class OrderService {
                            public Order load(String id) {
                                return new Order(id, 1);
                            }
                        }
                        """,
                StandardCharsets.UTF_8);

        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        SourceRoot projectSources = new SourceRoot(sourceRoot, parserConfiguration);
        List<ParseResult<CompilationUnit>> parsedSources = projectSources.tryToParse();
        List<ApiType> apiTypes = parsedSources.stream()
                .map(ParseResult::getResult)
                .map(result -> result.orElseThrow())
                .map(this::apiType)
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .collect(Collectors.toList());

        assertEquals(
                List.of(
                        new ApiType("Order", "com.acme.orders", List.of()),
                        new ApiType("OrderService", "com.acme.orders", List.of("load"))),
                apiTypes);
        assertTrue(
                parsedSources.stream().allMatch(ParseResult::isSuccessful),
                () -> parsedSources.stream().map(ParseResult::getProblems).toList().toString());
    }

    private ApiType apiType(CompilationUnit compilationUnit) {
        TypeDeclaration<?> type = compilationUnit.getPrimaryType().orElseThrow();
        List<String> publicMethods = type.getMembers().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .filter(method -> method.isPublic())
                .map(method -> method.getNameAsString())
                .sorted()
                .collect(Collectors.toList());
        return new ApiType(
                type.getNameAsString(),
                compilationUnit.getPackageDeclaration().orElseThrow().getNameAsString(),
                publicMethods);
    }

    private record ApiType(String name, String packageName, List<String> publicMethods) {
    }
}
