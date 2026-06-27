/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_modules.jboss_modules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import sun.misc.Unsafe;

public class Jboss_modulesTest {
    @TempDir
    Path repositoryRoot;

    @BeforeEach
    void seedParallelLoaders() throws ReflectiveOperationException {
        // Native image substitutes ClassLoader$ParallelLoaders without seeding ClassLoader.class.
        addParallelLoaderType(ClassLoader.class);
    }

    @Test
    void loadsModuleWithDirectoryResourceRootAndProperties() throws Exception {
        Path moduleDirectory = createModuleDirectory("example.resources");
        Path contentDirectory = moduleDirectory.resolve("content");
        Files.createDirectories(contentDirectory.resolve("messages"));
        Files.writeString(contentDirectory.resolve("messages/greeting.txt"), "hello from a directory root", UTF_8);
        writeModuleXml(moduleDirectory, "example.resources", """
                <resources>
                    <resource-root path="content"/>
                </resources>
                <properties>
                    <property name="module.kind" value="directory"/>
                    <property name="module.owner" value="integration-test"/>
                </properties>
                """);

        try (LocalModuleLoader loader = new LocalModuleLoader(new File[] {repositoryRoot.toFile() })) {
            Module module = loader.loadModule("example.resources");

            assertThat(module.getName()).isEqualTo("example.resources");
            assertThat(module.getProperty("module.kind")).isEqualTo("directory");
            assertThat(module.getProperty("missing.property", "fallback")).isEqualTo("fallback");
            assertThat(module.getPropertyNames()).contains("module.owner", "module.kind");
            assertThat(readText(module.getExportedResource("messages/greeting.txt")))
                    .isEqualTo("hello from a directory root");
            assertThat(readText(module.getClassLoader().getResource("messages/greeting.txt")))
                    .isEqualTo("hello from a directory root");
        }
    }

    @Test
    void loadsResourcesFromJarResourceRoot() throws Exception {
        Path moduleDirectory = createModuleDirectory("example.jarroot");
        Path jarPath = moduleDirectory.resolve("support.jar");
        writeJar(jarPath, "config/settings.properties", "feature.enabled=true\n");
        writeModuleXml(moduleDirectory, "example.jarroot", """
                <resources>
                    <resource-root path="support.jar"/>
                </resources>
                """);

        try (LocalModuleLoader loader = new LocalModuleLoader(new File[] {repositoryRoot.toFile() })) {
            Module module = loader.loadModule("example.jarroot");

            URL exportedResource = module.getExportedResource("config/settings.properties");
            assertThat(exportedResource).isNotNull();
            assertThat(exportedResource.toString()).contains("support.jar");
            assertThat(readText(exportedResource)).isEqualTo("feature.enabled=true\n");

            Enumeration<URL> resources = module.getExportedResources("config/settings.properties");
            List<URL> urls = Collections.list(resources);
            assertThat(urls).hasSize(1);
            assertThat(readText(urls.get(0))).isEqualTo("feature.enabled=true\n");
        }
    }

    @Test
    void appliesResourceRootFiltersFromModuleDescriptor() throws Exception {
        Path moduleDirectory = createModuleDirectory("example.filtered");
        Path resources = moduleDirectory.resolve("resources");
        Files.createDirectories(resources.resolve("public"));
        Files.createDirectories(resources.resolve("private"));
        Files.writeString(resources.resolve("public/visible.txt"), "visible resource", UTF_8);
        Files.writeString(resources.resolve("private/secret.txt"), "hidden resource", UTF_8);
        writeModuleXml(moduleDirectory, "example.filtered", """
                <resources>
                    <resource-root path="resources">
                        <filter>
                            <include path="public"/>
                            <exclude path="**"/>
                        </filter>
                    </resource-root>
                </resources>
                """);

        try (LocalModuleLoader loader = new LocalModuleLoader(new File[] {repositoryRoot.toFile() })) {
            Module module = loader.loadModule("example.filtered");

            assertThat(readText(module.getExportedResource("public/visible.txt"))).isEqualTo("visible resource");
            assertThat(readText(module.getClassLoader().getResource("public/visible.txt"))).isEqualTo("visible resource");
            assertThat(module.getExportedResource("private/secret.txt")).isNull();
            assertThat(module.getClassLoader().getResource("private/secret.txt")).isNull();
        }
    }

    @Test
    void resolvesResourcesFromModuleDependency() throws Exception {
        Path apiDirectory = createModuleDirectory("example.api");
        Path apiResources = apiDirectory.resolve("api-resources");
        Files.createDirectories(apiResources.resolve("api"));
        Files.writeString(apiResources.resolve("api/contract.txt"), "stable-api", UTF_8);
        writeModuleXml(apiDirectory, "example.api", """
                <resources>
                    <resource-root path="api-resources"/>
                </resources>
                """);

        Path applicationDirectory = createModuleDirectory("example.application");
        Path applicationResources = applicationDirectory.resolve("application-resources");
        Files.createDirectories(applicationResources.resolve("application"));
        Files.writeString(applicationResources.resolve("application/name.txt"), "consumer", UTF_8);
        writeModuleXml(applicationDirectory, "example.application", """
                <resources>
                    <resource-root path="application-resources"/>
                </resources>
                <dependencies>
                    <module name="example.api" export="true"/>
                </dependencies>
                """);

        try (LocalModuleLoader loader = new LocalModuleLoader(new File[] {repositoryRoot.toFile() })) {
            Module application = loader.loadModule("example.application");

            assertThat(readText(application.getClassLoader().getResource("application/name.txt")))
                    .isEqualTo("consumer");
            assertThat(readText(application.getClassLoader().getResource("api/contract.txt")))
                    .isEqualTo("stable-api");
            assertThat(readText(application.getExportedResource("api/contract.txt"))).isEqualTo("stable-api");
        }
    }

    @Test
    void resolvesModuleAliasToTargetModule() throws Exception {
        Path targetDirectory = createModuleDirectory("example.alias.target");
        Path resources = targetDirectory.resolve("resources");
        Files.createDirectories(resources.resolve("alias"));
        Files.writeString(resources.resolve("alias/value.txt"), "from target module", UTF_8);
        writeModuleXml(targetDirectory, "example.alias.target", """
                <resources>
                    <resource-root path="resources"/>
                </resources>
                """);

        Path aliasDirectory = createModuleDirectory("example.alias.name");
        writeModuleAliasXml(aliasDirectory, "example.alias.name", "example.alias.target");

        try (LocalModuleLoader loader = new LocalModuleLoader(new File[] {repositoryRoot.toFile() })) {
            Module target = loader.loadModule("example.alias.target");
            Module alias = loader.loadModule("example.alias.name");

            assertThat(alias).isSameAs(target);
            assertThat(alias.getName()).isEqualTo("example.alias.target");
            assertThat(readText(alias.getExportedResource("alias/value.txt"))).isEqualTo("from target module");
        }
    }

    @Test
    void defaultLocalModuleLoaderUsesModulePathSystemProperty() throws Exception {
        Path moduleDirectory = createModuleDirectory("example.defaultloader");
        Path resources = moduleDirectory.resolve("resources");
        Files.createDirectories(resources.resolve("defaultloader"));
        Files.writeString(resources.resolve("defaultloader/value.txt"), "from module.path", UTF_8);
        writeModuleXml(moduleDirectory, "example.defaultloader", """
                <resources>
                    <resource-root path="resources"/>
                </resources>
                """);

        String previousModulePath = System.getProperty("module.path");
        System.setProperty("module.path", repositoryRoot.toString());
        try (LocalModuleLoader loader = new LocalModuleLoader()) {
            Module module = loader.loadModule("example.defaultloader");

            assertThat(readText(module.getExportedResource("defaultloader/value.txt"))).isEqualTo("from module.path");
        } finally {
            if (previousModulePath == null) {
                System.clearProperty("module.path");
            } else {
                System.setProperty("module.path", previousModulePath);
            }
        }
    }

    private Path createModuleDirectory(String moduleName) throws IOException {
        Path directory = repositoryRoot.resolve(moduleName.replace('.', File.separatorChar)).resolve("main");
        Files.createDirectories(directory);
        return directory;
    }

    private static void writeModuleXml(Path moduleDirectory, String moduleName, String body) throws IOException {
        String moduleXml = """
                <module xmlns="urn:jboss:module:1.9" name="%s">
                %s</module>
                """.formatted(moduleName, body);
        Files.writeString(moduleDirectory.resolve("module.xml"), moduleXml, UTF_8);
    }

    private static void writeModuleAliasXml(Path moduleDirectory, String aliasName, String targetName) throws IOException {
        String moduleXml = """
                <module-alias xmlns="urn:jboss:module:1.9" name="%s" target-name="%s"/>
                """.formatted(aliasName, targetName);
        Files.writeString(moduleDirectory.resolve("module.xml"), moduleXml, UTF_8);
    }

    private static void writeJar(Path jarPath, String entryName, String content) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            output.putNextEntry(entry);
            output.write(content.getBytes(UTF_8));
            output.closeEntry();
        }
    }

    private static String readText(URL resource) throws IOException {
        assertThat(resource).isNotNull();
        try (InputStream input = resource.openStream()) {
            return new String(input.readAllBytes(), UTF_8);
        }
    }

    private static void addParallelLoaderType(Class<? extends ClassLoader> classLoaderType)
            throws ReflectiveOperationException {
        Class<?> parallelLoadersClass = Class.forName("java.lang.ClassLoader$ParallelLoaders");
        Field loaderTypesField = parallelLoadersClass.getDeclaredField("loaderTypes");
        Set<Class<? extends ClassLoader>> loaderTypes = getStaticFieldValue(loaderTypesField);
        loaderTypes.add(classLoaderType);
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends ClassLoader>> getStaticFieldValue(Field field)
            throws ReflectiveOperationException {
        Unsafe unsafe = getUnsafe();
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        return (Set<Class<? extends ClassLoader>>) unsafe.getObject(base, offset);
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }
}
