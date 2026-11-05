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
import com.github.javaparser.utils.Pair;
import com.github.javaparser.utils.SourceZip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises JavaParser's source-archive subsystem to catalog Java files supplied as a project bundle.
public class SourceArchiveCatalogTest {
    @Test
    public void catalogsJavaSourcesFromAnUploadedProjectArchive(@TempDir Path temporaryDirectory)
            throws IOException {
        Path archive = temporaryDirectory.resolve("orders-source.zip");
        writeArchive(archive);

        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        List<Pair<Path, ParseResult<CompilationUnit>>> parsedSources = new SourceZip(archive, configuration)
                .parse();
        Map<String, CompilationUnit> sourcesByPath = parsedSources.stream()
                .collect(Collectors.toMap(
                        source -> source.a.toString(),
                        source -> source.b.getResult().orElseThrow()));

        assertEquals(List.of("src/main/java/com/acme/orders/Order.java", "src/main/java/com/acme/orders/OrderService.java"),
                parsedSources.stream().map(source -> source.a.toString()).toList());
        assertTrue(parsedSources.stream().allMatch(source -> source.b.isSuccessful()));
        assertEquals("Order", primaryType(sourcesByPath.get(
                "src/main/java/com/acme/orders/Order.java")).getNameAsString());
        assertEquals(
                List.of("load"),
                sourcesByPath.get("src/main/java/com/acme/orders/OrderService.java")
                        .findAll(MethodDeclaration.class).stream()
                        .map(MethodDeclaration::getNameAsString)
                        .toList());
    }

    private void writeArchive(Path archive) throws IOException {
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(
                    zip,
                    "src/main/java/com/acme/orders/Order.java",
                    """
                    package com.acme.orders;

                    public record Order(String id, int itemCount) {
                    }
                    """);
            addEntry(
                    zip,
                    "src/main/java/com/acme/orders/OrderService.java",
                    """
                    package com.acme.orders;

                    public class OrderService {
                        public Order load(String id) {
                            return new Order(id, 1);
                        }
                    }
                    """);
            addEntry(zip, "README.txt", "source bundle");
        }
    }

    private void addEntry(ZipOutputStream zip, String path, String source) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(source.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private TypeDeclaration<?> primaryType(CompilationUnit compilationUnit) {
        return compilationUnit.findFirst(TypeDeclaration.class).orElseThrow();
    }
}
