/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_equo_ide.solstice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.equo.ide.Catalog;
import dev.equo.ide.IdeHookWelcome;
import dev.equo.ide.IdeLockFile;
import dev.equo.ide.WorkspaceInit;
import dev.equo.solstice.Capability;
import dev.equo.solstice.SolsticeManifest;
import dev.equo.solstice.p2.ConsoleTable;
import dev.equo.solstice.p2.P2Model;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
public class SolsticeTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesBundleManifestFromJarAndStripsOptionalRequirements() throws IOException {
        Path bundle = createBundleJar(
                "sample-bundle.jar",
                "com.example.bundle;singleton:=true",
                "1.2.3",
                "org.eclipse.osgi;bundle-version=\"[3.0.0,4.0.0)\","
                        + "com.example.required;visibility:=reexport,"
                        + "com.example.optional;resolution:=optional",
                "java.util,com.example.imported;version=\"[1.0.0,2.0.0)\","
                        + "com.example.optional.pkg;resolution:=optional",
                "com.example.api;version=\"1.0.0\",com.example.internal;uses:=\"com.example.api\"");

        SolsticeManifest manifest = SolsticeManifest.parseJar(bundle.toFile());

        assertThat(manifest.getSymbolicName()).isEqualTo("com.example.bundle");
        assertThat(manifest.getVersion()).isEqualTo(new Version(1, 2, 3));
        assertThat(manifest.getJarUrl()).startsWith("jar:file:").endsWith("sample-bundle.jar!");
        assertThat(manifest.toString()).isEqualTo("com.example.bundle");
        assertThat(manifest.totalRequiredBundles())
                .containsExactly("com.example.required", "org.eclipse.osgi");
        assertThat(manifest.totalPkgImports()).containsExactly("com.example.imported", "java.util");
        assertThat(manifest.totalPkgExports()).containsExactly("com.example.api", "com.example.internal");
        assertThat(manifest.getHeadersOriginal())
                .containsEntry(Constants.BUNDLE_VERSION, "1.2.3")
                .containsKey(Constants.EXPORT_PACKAGE);
    }

    @Test
    void capabilitiesSupportSubsetMatchingAndSupersetLookup() {
        Capability full = new Capability("osgi.service");
        full.add("objectClass", "com.example.Service");
        full.add("effective", "active");
        full.add("version", "1");

        Capability sameValuesDifferentOrder = new Capability("osgi.service");
        sameValuesDifferentOrder.add("version", "1");
        sameValuesDifferentOrder.add("objectClass", "com.example.Service");
        sameValuesDifferentOrder.add("effective", "active");

        Capability subset = new Capability("osgi.service", "objectClass", "com.example.Service");
        Capability missing = new Capability("osgi.service", "objectClass", "com.example.Other");
        Capability.SupersetMap<String> services = new Capability.SupersetMap<>();
        services.put(full, "registered-service");

        assertThat(full.size()).isEqualTo(3);
        assertThat(full.getValue("effective")).isEqualTo("active");
        assertThat(full.getValue("missing")).isNull();
        assertThat(full.isSupersetOf(subset)).isTrue();
        assertThat(subset.isSubsetOf(full)).isTrue();
        assertThat(subset.isSubsetOfElementIn(List.of(full))).isTrue();
        assertThat(missing.isSubsetOf(full)).isFalse();
        assertThat(full).isNotEqualTo(sameValuesDifferentOrder);
        assertThat(full.toString()).isEqualTo("osgi.service:objectClass=com.example.Service,effective=active,version=1");
        assertThat(services.getAnySupersetOf(subset)).isEqualTo("registered-service");
        assertThat(services.getAnySupersetOf(missing)).isNull();
    }

    @Test
    void p2ModelFiltersValidateConflictsAndCanBeRendered() {
        P2Model model = new P2Model();
        model.addP2Repo("https://download.example.test/repository/");
        model.getInstall().add("com.example.feature.feature.group");
        model.getPureMaven().add("com.example:tooling:1");
        model.addFilterAndValidate(
                "runtime",
                filter -> filter.exclude("com.example.excluded")
                        .excludePrefix("com.example.internal.")
                        .excludeSuffix(".source")
                        .prop("osgi.os", "linux"));

        P2Model copy = model.deepCopy();
        copy.getInstall().add("com.example.extra.feature.group");

        assertThat(model.isEmpty()).isFalse();
        assertThat(copy).isNotEqualTo(model);
        assertThat(model.getInstall()).containsExactly("com.example.feature.feature.group");
        assertThat(model.toString())
                .contains("'useMavenCentral': true")
                .contains("p2repo")
                .contains("pureMaven");
        assertThat(ConsoleTable.request(model, ConsoleTable.Format.csv))
                .contains("p2repo,https://download.example.test/repository/")
                .contains("install,com.example.feature.feature.group")
                .contains("maven,com.example:tooling:1");
        assertThat(ConsoleTable.mavenStatus(List.of(), ConsoleTable.Format.ascii))
                .isEqualTo("No jars were specified.");
        assertThatThrownBy(() -> model.addP2Repo("https://download.example.test/repository"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Must end with /");
        assertThatThrownBy(
                        () -> model.addFilterAndValidate(
                                "conflicting-runtime", filter -> filter.prop("osgi.os", "macosx")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflict for prop osgi.os");
        assertThatThrownBy(() -> model.removeFilter("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such filter exists");
    }

    @Test
    void nativePlatformFilterIsOnlyAppliedWhenNoPlatformFilterExists() {
        P2Model model = new P2Model();

        model.applyNativeFilterIfNoPlatformFilter();
        model.applyNativeFilterIfNoPlatformFilter();

        assertThat(model.getFilters()).containsOnlyKeys("platform-specific-for-running");
        assertThat(model.getFilters().get("platform-specific-for-running").getProps())
                .containsKeys("osgi.os", "osgi.ws", "osgi.arch");
    }

    @Test
    void catalogEntriesExposeUrlsTargetsRequirementsAndFilters() {
        assertThat(Catalog.isUrl("https://download.example.test/repository/")).isTrue();
        assertThat(Catalog.isUrl("4.28")).isFalse();
        assertThat(Catalog.JDT.getName()).isEqualTo("jdt");
        assertThat(Catalog.JDT.getRequires()).contains(Catalog.PLATFORM);
        assertThat(Catalog.JDT.getTargetsFor(null))
                .containsExactly("org.eclipse.releng.java.languages.categoryIU");
        assertThat(Catalog.JDT.getUrlForOverride("4.28"))
                .isEqualTo("https://download.eclipse.org/eclipse/updates/4.28/");
        assertThat(Catalog.JDT.getUrlForOverride("https://download.example.test/p2/"))
                .isEqualTo("https://download.example.test/p2/");
        assertThat(Catalog.M2E.getFiltersFor(null))
                .containsOnlyKeys("m2e-nested-jar-has-lucene");
        assertThat(Catalog.CHATGPT.isPureMaven()).isTrue();
        assertThat(Catalog.PLATFORM.isPureMaven()).isFalse();
    }

    @Test
    void workspaceInitializersWriteAndCopyPreferenceFiles() throws IOException {
        WorkspaceInit base = new WorkspaceInit();
        Catalog.PLATFORM.showLineNumbers(base, true);
        Catalog.PLATFORM.showWhitespace(base, true);
        Catalog.PLATFORM.showLineEndings(base, false);

        WorkspaceInit copied = new WorkspaceInit();
        copied.copyAllFrom(base);
        Catalog.JDT.classpathVariable(copied, "TEST_LIB", tempDir.resolve("lib").toString());
        copied.applyTo(tempDir.toFile());

        Properties editorPrefs = loadProperties(tempDir.resolve(
                "instance/.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.ui.editors.prefs"));
        assertThat(editorPrefs)
                .containsEntry("eclipse.preferences.version", "1")
                .containsEntry("lineNumberRuler", "true")
                .containsEntry("showWhitespaceCharacters", "true")
                .containsEntry("showLineFeed", "false")
                .containsEntry("showCarriageReturn", "false");

        Properties jdtPrefs = loadProperties(tempDir.resolve(
                "instance/.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.jdt.core.prefs"));
        assertThat(jdtPrefs)
                .containsEntry("org.eclipse.jdt.core.classpathVariable.TEST_LIB", tempDir.resolve("lib").toString());
    }

    @Test
    void simpleIdeUtilitiesExposeStateWithoutStartingUi() {
        IdeHookWelcome welcome = new IdeHookWelcome()
                .openUrl("https://example.test/welcome")
                .perspective("com.example.Perspective");
        IdeLockFile lockFile = IdeLockFile.forWorkspaceDir(tempDir.toFile());

        assertThat(welcome.perspective()).isEqualTo("com.example.Perspective");
        assertThat(lockFile.hasClasspath()).isFalse();
        assertThat(lockFile.ideAlreadyRunning()).isNull();
    }

    private Path createBundleJar(
            String filename,
            String symbolicName,
            String version,
            String requireBundle,
            String importPackage,
            String exportPackage)
            throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
        attributes.putValue(Constants.BUNDLE_VERSION, version);
        attributes.putValue(Constants.REQUIRE_BUNDLE, requireBundle);
        attributes.putValue(Constants.IMPORT_PACKAGE, importPackage);
        attributes.putValue(Constants.EXPORT_PACKAGE, exportPackage);

        Path jar = tempDir.resolve(filename);
        try (OutputStream output = Files.newOutputStream(jar);
                JarOutputStream ignored = new JarOutputStream(output, manifest)) {
            // The manifest is enough for SolsticeManifest.parseJar.
        }
        return jar;
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
