/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.apache.maven.archiver.ManifestConfiguration;
import org.apache.maven.archiver.ManifestSection;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_archiverTest {
    @TempDir
    Path tempDir;

    @Test
    void configurationObjectsRetainValuesAndNormalizeClasspathPrefix() throws IOException {
        MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();
        assertThat(archiveConfiguration.isCompress()).isTrue();
        assertThat(archiveConfiguration.isIndex()).isFalse();
        assertThat(archiveConfiguration.isAddMavenDescriptor()).isTrue();
        assertThat(archiveConfiguration.isForced()).isTrue();
        assertThat(archiveConfiguration.isManifestEntriesEmpty()).isTrue();
        assertThat(archiveConfiguration.isManifestSectionsEmpty()).isTrue();
        assertThat(archiveConfiguration.getManifest()).isSameAs(archiveConfiguration.getManifest());

        File manifestFile = tempDir.resolve("MANIFEST.MF").toFile();
        File pomPropertiesFile = tempDir.resolve("custom-pom.properties").toFile();
        archiveConfiguration.setCompress(false);
        archiveConfiguration.setIndex(true);
        archiveConfiguration.setAddMavenDescriptor(false);
        archiveConfiguration.setForced(false);
        archiveConfiguration.setManifestFile(manifestFile);
        archiveConfiguration.setPomPropertiesFile(pomPropertiesFile);
        archiveConfiguration.addManifestEntry("X-Entry", "one");
        archiveConfiguration.addManifestEntries(Collections.singletonMap("X-Other", "two"));

        ManifestSection section = new ManifestSection();
        section.setName("dependencies/sample.jar");
        section.addManifestEntry("SHA-256-Digest", "digest-value");
        archiveConfiguration.addManifestSection(section);

        assertThat(archiveConfiguration.isCompress()).isFalse();
        assertThat(archiveConfiguration.isIndex()).isTrue();
        assertThat(archiveConfiguration.isAddMavenDescriptor()).isFalse();
        assertThat(archiveConfiguration.isForced()).isFalse();
        assertThat(archiveConfiguration.getManifestFile()).isEqualTo(manifestFile);
        assertThat(archiveConfiguration.getPomPropertiesFile()).isEqualTo(pomPropertiesFile);
        assertThat(archiveConfiguration.getManifestEntries())
                .containsEntry("X-Entry", "one")
                .containsEntry("X-Other", "two");
        assertThat(archiveConfiguration.getManifestSections()).containsExactly(section);
        assertThat(section.getName()).isEqualTo("dependencies/sample.jar");
        assertThat(section.isManifestEntriesEmpty()).isFalse();
        assertThat(section.getManifestEntries()).containsEntry("SHA-256-Digest", "digest-value");

        ManifestConfiguration manifestConfiguration = new ManifestConfiguration();
        assertThat(manifestConfiguration.getClasspathPrefix()).isEmpty();
        assertThat(manifestConfiguration.getClasspathLayoutType())
                .isEqualTo(ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_SIMPLE);
        assertThat(manifestConfiguration.isUseUniqueVersions()).isTrue();

        manifestConfiguration.setClasspathPrefix("lib\\nested");
        manifestConfiguration.setClasspathMavenRepositoryLayout(true);
        assertThat(manifestConfiguration.getClasspathPrefix()).isEqualTo("lib/nested/");
        assertThat(manifestConfiguration.getClasspathLayoutType())
                .isEqualTo(ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_REPOSITORY);

        manifestConfiguration.setClasspathLayoutType(ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM);
        manifestConfiguration.setCustomClasspathLayout(
                "${artifact.groupId}/${artifact.artifactId}.${artifact.extension}");
        manifestConfiguration.setUseUniqueVersions(false);
        manifestConfiguration.setAddClasspath(true);
        manifestConfiguration.setAddExtensions(true);
        manifestConfiguration.setAddDefaultImplementationEntries(true);
        manifestConfiguration.setAddDefaultSpecificationEntries(true);
        manifestConfiguration.setMainClass("example.Main");
        manifestConfiguration.setPackageName("example.package");
        assertThat(manifestConfiguration.getClasspathLayoutType())
                .isEqualTo(ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM);
        assertThat(manifestConfiguration.getCustomClasspathLayout())
                .isEqualTo("${artifact.groupId}/${artifact.artifactId}.${artifact.extension}");
        assertThat(manifestConfiguration.isUseUniqueVersions()).isFalse();
        assertThat(manifestConfiguration.isAddClasspath()).isTrue();
        assertThat(manifestConfiguration.isAddExtensions()).isTrue();
        assertThat(manifestConfiguration.isAddDefaultImplementationEntries()).isTrue();
        assertThat(manifestConfiguration.isAddDefaultSpecificationEntries()).isTrue();
        assertThat(manifestConfiguration.getMainClass()).isEqualTo("example.Main");
        assertThat(manifestConfiguration.getPackageName()).isEqualTo("example.package");
    }

    @Test
    void manifestCreatedByIncludesMavenVersionFromSession() throws Exception {
        MavenProject project = newProject("org.example", "session-app", "1.0.0");
        Properties executionProperties = new Properties();
        executionProperties.setProperty("maven.version", "test-session-version");
        MavenSession session = new MavenSession(null, null, null, null, null, Collections.emptyList(),
                tempDir.toString(), executionProperties, new Date(0L));

        Manifest manifest = new MavenArchiver().getManifest(session, project, new ManifestConfiguration());

        assertThat(mainAttribute(manifest, "Created-By")).isEqualTo("Apache Maven test-session-version");
    }

    @Test
    void manifestIncludesDefaultCustomAndSectionEntries() throws Exception {
        MavenProject project = newProject("org.example", "demo-app", "1.2.3");
        Organization organization = new Organization();
        organization.setName("Example Foundation");
        project.setOrganization(organization);

        MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();
        ManifestConfiguration manifestConfiguration = archiveConfiguration.getManifest();
        manifestConfiguration.setMainClass("org.example.Main");
        manifestConfiguration.setPackageName("org.example.package");
        manifestConfiguration.setAddDefaultImplementationEntries(true);
        manifestConfiguration.setAddDefaultSpecificationEntries(true);
        archiveConfiguration.addManifestEntry("Created-By", "custom build tool");
        archiveConfiguration.addManifestEntry("X-Custom", "custom-value");

        ManifestSection section = new ManifestSection();
        section.setName("plugins/sample-plugin.jar");
        section.addManifestEntry("Plugin-Id", "sample-plugin");
        archiveConfiguration.addManifestSection(section);

        Manifest manifest = new MavenArchiver().getManifest(project, archiveConfiguration);

        assertThat(mainAttribute(manifest, "Created-By")).isEqualTo("custom build tool");
        assertThat(mainAttribute(manifest, "Built-By")).isNotNull();
        assertThat(mainAttribute(manifest, "Build-Jdk")).isEqualTo(System.getProperty("java.version"));
        assertThat(mainAttribute(manifest, "Package")).isEqualTo("org.example.package");
        assertThat(mainAttribute(manifest, "Main-Class")).isEqualTo("org.example.Main");
        assertThat(mainAttribute(manifest, "X-Custom")).isEqualTo("custom-value");
        assertThat(mainAttribute(manifest, "Specification-Title")).isEqualTo("Demo App");
        assertThat(mainAttribute(manifest, "Specification-Version")).isEqualTo("1.2.3");
        assertThat(mainAttribute(manifest, "Specification-Vendor")).isEqualTo("Example Foundation");
        assertThat(mainAttribute(manifest, "Implementation-Title")).isEqualTo("Demo App");
        assertThat(mainAttribute(manifest, "Implementation-Version")).isEqualTo("1.2.3");
        assertThat(mainAttribute(manifest, "Implementation-Vendor-Id")).isEqualTo("org.example");
        assertThat(mainAttribute(manifest, "Implementation-Vendor")).isEqualTo("Example Foundation");
        assertThat(manifest.getSection("plugins/sample-plugin.jar").getAttributeValue("Plugin-Id"))
                .isEqualTo("sample-plugin");
    }

    @Test
    void manifestBuildsClasspathWithSimpleRepositoryAndCustomLayouts() throws Exception {
        File alphaJar = createEmptyJar("alpha-1.0.0.jar");
        File betaJar = createEmptyJar("beta-2.0.0-tests.jar");
        Artifact alpha = newArtifact("com.acme", "alpha", "1.0.0", null, "compile", alphaJar);
        Artifact beta = newArtifact("org.sample", "beta", "2.0.0", "tests", "runtime", betaJar);
        MavenProject project = newProject("org.example", "classpath-app", "1.0.0", alpha, beta);

        assertThat(classPathFor(project, ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_SIMPLE, "lib", null, true))
                .isEqualTo("lib/alpha-1.0.0.jar lib/beta-2.0.0-tests.jar");
        assertThat(classPathFor(project, ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_REPOSITORY, "repo", null, true))
                .isEqualTo("repo/com/acme/alpha/1.0.0/alpha-1.0.0.jar "
                        + "repo/org/sample/beta/2.0.0/beta-2.0.0-tests.jar");
        assertThat(classPathFor(project, ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM, "", "modules/"
                + "${artifact.groupIdPath}/${artifact.artifactId}${dashClassifier}.${artifact.extension}", true))
                .isEqualTo("modules/com/acme/alpha.jar modules/org/sample/beta-tests.jar");
    }

    @Test
    void manifestAddsExtensionEntriesForJarArtifactsOnly() throws Exception {
        Artifact extension = newArtifact("com.acme", "feature.extension", "3.4.5", null, "runtime",
                createEmptyJar("feature-extension.jar"));
        extension.setRepository(new TestArtifactRepository("https://repo.example.invalid/releases"));
        Artifact webModule = newArtifact("com.acme", "web-module", "1.0.0", null, "runtime",
                createEmptyJar("web-module.war"), "war");
        MavenProject project = newProject("org.example", "extension-app", "1.0.0", extension, webModule);

        ManifestConfiguration manifestConfiguration = new ManifestConfiguration();
        manifestConfiguration.setAddExtensions(true);
        Manifest manifest = new MavenArchiver().getManifest(project, manifestConfiguration);

        assertThat(mainAttribute(manifest, "Extension-List")).isEqualTo("feature.extension");
        assertThat(mainAttribute(manifest, "feature_extension-Extension-Name")).isEqualTo("feature.extension");
        assertThat(mainAttribute(manifest, "feature_extension-Implementation-Version")).isEqualTo("3.4.5");
        assertThat(mainAttribute(manifest, "feature_extension-Implementation-URL"))
                .isEqualTo("https://repo.example.invalid/releases/com.acme:feature.extension:jar:3.4.5:runtime");
        assertThat(mainAttribute(manifest, "web-module-Extension-Name")).isNull();
    }

    @Test
    void manifestReportsUnsupportedClasspathLayouts() throws Exception {
        MavenProject project = newProject("org.example", "invalid-layout-app", "1.0.0",
                newArtifact("com.acme", "alpha", "1.0.0", null, "compile", createEmptyJar("alpha.jar")));

        ManifestConfiguration missingCustomLayout = new ManifestConfiguration();
        missingCustomLayout.setAddClasspath(true);
        missingCustomLayout.setClasspathLayoutType(ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM);
        assertThatExceptionOfType(ManifestException.class)
                .isThrownBy(() -> new MavenArchiver().getManifest(project, missingCustomLayout))
                .withMessageContaining("custom layout expression was not specified");

        ManifestConfiguration unknownLayout = new ManifestConfiguration();
        unknownLayout.setAddClasspath(true);
        unknownLayout.setClasspathLayoutType("unsupported-layout");
        assertThatExceptionOfType(ManifestException.class)
                .isThrownBy(() -> new MavenArchiver().getManifest(project, unknownLayout))
                .withMessageContaining("Unknown classpath layout type");
    }

    @Test
    void createArchiveMergesSuppliedManifestFileWithGeneratedEntries() throws Exception {
        MavenProject project = newProject("org.example", "manifest-file-app", "1.0.0");
        Path manifestFile = tempDir.resolve("MANIFEST.MF");
        Files.writeString(manifestFile, """
                Manifest-Version: 1.0
                X-External: from-file

                Name: docs/readme.txt
                X-Section: section-value

                """, StandardCharsets.UTF_8);

        MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();
        archiveConfiguration.setAddMavenDescriptor(false);
        archiveConfiguration.setManifestFile(manifestFile.toFile());
        archiveConfiguration.getManifest().setMainClass("org.example.ManifestFileMain");
        archiveConfiguration.addManifestEntry("X-Configured", "from-configuration");

        Path classesDirectory = tempDir.resolve("classes-manifest-file-app");
        Files.createDirectories(classesDirectory);
        Files.writeString(classesDirectory.resolve("content.txt"), "content", StandardCharsets.UTF_8);

        MavenArchiver mavenArchiver = new MavenArchiver();
        mavenArchiver.setArchiver(new JarArchiver());
        mavenArchiver.getArchiver().addDirectory(classesDirectory.toFile());
        Files.createDirectories(tempDir.resolve("target"));
        File archiveFile = tempDir.resolve("target/manifest-file-app.jar").toFile();
        mavenArchiver.setOutputFile(archiveFile);
        mavenArchiver.createArchive(project, archiveConfiguration);

        try (JarFile jarFile = new JarFile(archiveFile)) {
            java.util.jar.Manifest jarManifest = jarFile.getManifest();
            Attributes attributes = jarManifest.getMainAttributes();
            assertThat(attributes.getValue("X-External")).isEqualTo("from-file");
            assertThat(attributes.getValue("X-Configured")).isEqualTo("from-configuration");
            assertThat(attributes.getValue("Main-Class")).isEqualTo("org.example.ManifestFileMain");
            assertThat(jarManifest.getAttributes("docs/readme.txt").getValue("X-Section"))
                    .isEqualTo("section-value");
        }
    }

    @Test
    void createArchiveWritesManifestAndMavenDescriptors() throws Exception {
        MavenProject project = newProject("org.example", "archive-app", "2.0.0");
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>archive-app</artifactId>
                  <version>2.0.0</version>
                </project>
                """, StandardCharsets.UTF_8);
        project.setFile(pomFile.toFile());

        MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();
        archiveConfiguration.setPomPropertiesFile(tempDir.resolve("generated/custom-pom.properties").toFile());
        archiveConfiguration.getManifest().setMainClass("org.example.archive.Main");
        archiveConfiguration.addManifestEntry("X-Archive", "created");

        MavenArchiver mavenArchiver = new MavenArchiver();
        mavenArchiver.setArchiver(new JarArchiver());
        Files.createDirectories(tempDir.resolve("target"));
        File archiveFile = tempDir.resolve("target/archive-app.jar").toFile();
        mavenArchiver.setOutputFile(archiveFile);
        mavenArchiver.createArchive(project, archiveConfiguration);

        assertThat(archiveFile).isFile();
        try (JarFile jarFile = new JarFile(archiveFile)) {
            java.util.jar.Manifest jarManifest = jarFile.getManifest();
            Attributes attributes = jarManifest.getMainAttributes();
            assertThat(attributes.getValue("Main-Class")).isEqualTo("org.example.archive.Main");
            assertThat(attributes.getValue("X-Archive")).isEqualTo("created");
            assertThat(jarFile.getEntry("META-INF/maven/org.example/archive-app/pom.xml")).isNotNull();
            assertThat(jarFile.getEntry("META-INF/maven/org.example/archive-app/pom.properties")).isNotNull();
        }

        Properties properties = new Properties();
        File generatedProperties = tempDir.resolve("generated/custom-pom.properties").toFile();
        try (FileInputStream inputStream = new FileInputStream(generatedProperties)) {
            properties.load(inputStream);
        }
        assertThat(properties)
                .containsEntry("groupId", "org.example")
                .containsEntry("artifactId", "archive-app")
                .containsEntry("version", "2.0.0");
    }

    private String classPathFor(MavenProject project, String layoutType, String prefix, String customLayout,
            boolean uniqueVersions) throws Exception {
        ManifestConfiguration manifestConfiguration = new ManifestConfiguration();
        manifestConfiguration.setAddClasspath(true);
        manifestConfiguration.setClasspathLayoutType(layoutType);
        manifestConfiguration.setClasspathPrefix(prefix);
        manifestConfiguration.setCustomClasspathLayout(customLayout);
        manifestConfiguration.setUseUniqueVersions(uniqueVersions);
        Manifest manifest = new MavenArchiver().getManifest(project, manifestConfiguration);
        return mainAttribute(manifest, "Class-Path");
    }

    private MavenProject newProject(String groupId, String artifactId, String version, Artifact... artifacts)
            throws IOException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setName(toDisplayName(artifactId));
        model.setPackaging("jar");
        Build build = new Build();
        build.setDirectory(tempDir.resolve("target-" + artifactId).toString());
        build.setOutputDirectory(tempDir.resolve("classes-" + artifactId).toString());
        model.setBuild(build);

        MavenProject project = new MavenProject(model);
        project.setArtifact(newArtifact(groupId, artifactId, version, null, "compile", null));
        Set<Artifact> artifactSet = new LinkedHashSet<>(Arrays.asList(artifacts));
        project.setArtifacts(artifactSet);
        return project;
    }

    private Artifact newArtifact(String groupId, String artifactId, String version, String classifier, String scope,
            File file) {
        return newArtifact(groupId, artifactId, version, classifier, scope, file, "jar");
    }

    private Artifact newArtifact(String groupId, String artifactId, String version, String classifier, String scope,
            File file, String type) {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, VersionRange.createFromVersion(version), scope,
                type, classifier, new ClasspathArtifactHandler(type));
        artifact.setFile(file);
        return artifact;
    }

    private File createEmptyJar(String fileName) throws IOException {
        Path jarPath = tempDir.resolve(fileName);
        Files.write(jarPath, new byte[] { 'P', 'K', 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        return jarPath.toFile();
    }

    private String mainAttribute(Manifest manifest, String name) {
        return manifest.getMainSection().getAttributeValue(name);
    }

    private String toDisplayName(String artifactId) {
        String[] words = artifactId.split("-");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static final class ClasspathArtifactHandler implements ArtifactHandler {
        private final String type;

        private ClasspathArtifactHandler(String type) {
            this.type = type;
        }

        @Override
        public String getExtension() {
            return type;
        }

        @Override
        public String getClassifier() {
            return null;
        }

        @Override
        public String getDirectory() {
            return type + "s";
        }

        @Override
        public String getPackaging() {
            return type;
        }

        @Override
        public boolean isIncludesDependencies() {
            return false;
        }

        @Override
        public String getLanguage() {
            return "java";
        }

        @Override
        public boolean isAddedToClasspath() {
            return true;
        }
    }

    private static final class TestArtifactRepository implements ArtifactRepository {
        private final String url;

        private TestArtifactRepository(String url) {
            this.url = url;
        }

        @Override
        public String pathOf(Artifact artifact) {
            return artifact.toString();
        }

        @Override
        public String pathOfRemoteRepositoryMetadata(org.apache.maven.artifact.metadata.ArtifactMetadata metadata) {
            return metadata.toString();
        }

        @Override
        public String pathOfLocalRepositoryMetadata(org.apache.maven.artifact.metadata.ArtifactMetadata metadata,
                ArtifactRepository repository) {
            return metadata.toString();
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public String getBasedir() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "https";
        }

        @Override
        public String getId() {
            return "test";
        }

        @Override
        public ArtifactRepositoryPolicy getSnapshots() {
            return null;
        }

        @Override
        public ArtifactRepositoryPolicy getReleases() {
            return null;
        }

        @Override
        public ArtifactRepositoryLayout getLayout() {
            return null;
        }

        @Override
        public String getKey() {
            return "test";
        }

        @Override
        public boolean isUniqueVersion() {
            return true;
        }

        @Override
        public void setBlacklisted(boolean blacklisted) {
        }

        @Override
        public boolean isBlacklisted() {
            return false;
        }
    }
}
