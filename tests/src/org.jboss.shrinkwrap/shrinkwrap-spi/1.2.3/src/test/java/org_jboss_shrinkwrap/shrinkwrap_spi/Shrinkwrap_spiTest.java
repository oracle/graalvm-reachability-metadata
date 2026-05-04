/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchiveEventHandler;
import org.jboss.shrinkwrap.api.ArchiveFormat;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Assignable;
import org.jboss.shrinkwrap.api.Configuration;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.ExtensionLoader;
import org.jboss.shrinkwrap.api.Filter;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.NamedAsset;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.formatter.Formatter;
import org.jboss.shrinkwrap.spi.ArchiveFormatAssociable;
import org.jboss.shrinkwrap.spi.Configurable;
import org.jboss.shrinkwrap.spi.Identifiable;
import org.jboss.shrinkwrap.spi.MemoryMapArchive;
import org.junit.jupiter.api.Test;

public class Shrinkwrap_spiTest {
    @Test
    void configurableExposesTheExactConfigurationProvidedByTheRuntime() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            StubExtensionLoader extensionLoader = new StubExtensionLoader();
            ClassLoader classLoader = currentClassLoader();
            List<ClassLoader> classLoaders = Collections.singletonList(classLoader);
            Configuration configuration = new ConfigurationBuilder()
                    .extensionLoader(extensionLoader)
                    .executorService(executorService)
                    .classLoaders(classLoaders)
                    .build();

            SimpleConfigurable configurable = new SimpleConfigurable(configuration);

            assertThat(configurable.getConfiguration()).isSameAs(configuration);
            assertThat(configurable.getConfiguration().getExtensionLoader()).isSameAs(extensionLoader);
            assertThat(configurable.getConfiguration().getExecutorService()).isSameAs(executorService);
            assertThat(configurable.getConfiguration().getClassLoaders()).containsExactly(classLoader);
            assertThat(configurable.as(Configurable.class)).isSameAs(configurable);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void identifiableMaintainsIdsThroughTheSpiContract() {
        SimpleIdentifiable identifiable = new SimpleIdentifiable("archive-one");

        assertThat(identifiable.getId()).isEqualTo("archive-one");
        assertThat(identifiable.as(Identifiable.class)).isSameAs(identifiable);

        identifiable.setId("archive-two");

        assertThat(identifiable.getId()).isEqualTo("archive-two");
        assertThatThrownBy(() -> identifiable.setId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> identifiable.setId("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
        assertThat(identifiable.getId()).isEqualTo("archive-two");
    }

    @Test
    void archiveFormatAssociableReportsEverySupportedArchiveFormat() {
        for (ArchiveFormat archiveFormat : ArchiveFormat.values()) {
            SimpleArchiveFormatAssociable associable = new SimpleArchiveFormatAssociable(archiveFormat);

            assertThat(associable.getArchiveFormat()).isSameAs(archiveFormat);
        }
    }

    @Test
    void extensionLoaderCanExposeSpiImplementationsByPublicApiType() {
        StubExtensionLoader extensionLoader = new StubExtensionLoader()
                .addOverride(Configurable.class, SimpleConfigurable.class)
                .addOverride(Identifiable.class, SimpleIdentifiable.class)
                .addArchiveFormat(MemoryMapArchive.class, ArchiveFormat.ZIP);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Configuration configuration = configuration(extensionLoader, executorService);
            MemoryMapArchive archive = new InMemoryMemoryMapArchive("sample.zip", configuration, ArchiveFormat.ZIP);

            Configurable configurable = extensionLoader.load(Configurable.class, archive);
            Identifiable identifiable = extensionLoader.load(Identifiable.class, archive);

            assertThat(configurable).isSameAs(archive);
            assertThat(configurable.getConfiguration()).isSameAs(configuration);
            assertThat(identifiable).isSameAs(archive);
            assertThat(identifiable.getId()).isEqualTo("sample.zip");
            assertThat(extensionLoader.getExtensionFromExtensionMapping(Configurable.class))
                    .isEqualTo(SimpleConfigurable.class.getName());
            assertThat(extensionLoader.getArchiveFormatFromExtensionMapping(MemoryMapArchive.class))
                    .isSameAs(ArchiveFormat.ZIP);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void memoryMapArchiveBehavesAsAnArchiveAndSpiExtension() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Configuration configuration = configuration(new StubExtensionLoader(), executorService);
            MemoryMapArchive archive = new InMemoryMemoryMapArchive(
                    "application.zip", configuration, ArchiveFormat.ZIP);
            ArchivePath messagePath = path("/WEB-INF/classes/message.txt");
            NamedAsset descriptor = new TestNamedAsset("META-INF/shrinkwrap.txt", "descriptor");

            archive.addAsDirectory("/WEB-INF/classes")
                    .add(new TestAsset("hello"), messagePath)
                    .add(descriptor);

            assertThat(archive.getName()).isEqualTo("application.zip");
            assertThat(archive.getId()).isEqualTo("application.zip");
            assertThat(archive.contains(messagePath)).isTrue();
            assertThat(archive.contains("META-INF/shrinkwrap.txt")).isTrue();
            assertThat(readAsset(archive.get(messagePath).getAsset())).isEqualTo("hello");
            assertThat(readAsset(archive.get("META-INF/shrinkwrap.txt").getAsset())).isEqualTo("descriptor");
            assertThat(archive.getContent(path -> path.get().endsWith("message.txt"))).containsOnlyKeys(messagePath);
            assertThat(archive.as(Configurable.class).getConfiguration()).isSameAs(configuration);
            assertThat(((ArchiveFormatAssociable) archive).getArchiveFormat()).isSameAs(ArchiveFormat.ZIP);

            archive.move(messagePath, path("/WEB-INF/classes/renamed.txt"));

            assertThat(archive.contains(messagePath)).isFalse();
            assertThat(archive.contains("/WEB-INF/classes/renamed.txt")).isTrue();

            Node deleted = archive.delete("META-INF/shrinkwrap.txt");

            assertThat(readAsset(deleted.getAsset())).isEqualTo("descriptor");
            assertThat(archive.contains("META-INF/shrinkwrap.txt")).isFalse();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void memoryMapArchiveSupportsCopiesMergesFormattingAndWriting() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Configuration configuration = configuration(new StubExtensionLoader(), executorService);
            InMemoryMemoryMapArchive source = new InMemoryMemoryMapArchive("source", configuration, ArchiveFormat.ZIP);
            InMemoryMemoryMapArchive target = new InMemoryMemoryMapArchive("target", configuration, ArchiveFormat.TAR);
            ArchivePath alphaPath = path("alpha.txt");
            ArchivePath betaPath = path("beta.txt");
            source.add(new TestAsset("alpha"), alphaPath);
            target.add(new TestAsset("beta"), betaPath);

            Archive<MemoryMapArchive> copy = source.shallowCopy();
            target.merge(source);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            target.writeTo(outputStream, archive -> archive.getName() + ":" + archive.getContent().size());

            assertThat(copy).isNotSameAs(source);
            assertThat(copy.getContent()).containsOnlyKeys(alphaPath);
            assertThat(target.getContent()).containsOnlyKeys(alphaPath, betaPath);
            assertThat(target.toString(archive -> archive.getName() + " entries=" + archive.getContent().size()))
                    .isEqualTo("target entries=2");
            assertThat(new String(outputStream.toByteArray(), StandardCharsets.UTF_8)).isEqualTo("target:2");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void memoryMapArchiveSelectivelyMergesContentUnderTargetPath() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Configuration configuration = configuration(new StubExtensionLoader(), executorService);
            InMemoryMemoryMapArchive source = new InMemoryMemoryMapArchive("source", configuration, ArchiveFormat.ZIP);
            InMemoryMemoryMapArchive target = new InMemoryMemoryMapArchive("target", configuration, ArchiveFormat.ZIP);
            ArchivePath stagingPath = path("/staging");
            ArchivePath mergedPath = path("/merged");
            ArchivePath keepPath = path("/assets/keep.txt");
            ArchivePath skipPath = path("/assets/skip.txt");
            ArchivePath readmePath = path("/docs/readme.txt");

            source.add(new TestAsset("keep"), keepPath)
                    .add(new TestAsset("skip"), skipPath)
                    .add(new TestAsset("readme"), readmePath);
            target.addAsDirectories(stagingPath, mergedPath);
            target.merge(source, mergedPath, path -> !path.get().endsWith("skip.txt"));

            assertThat(target.contains(stagingPath)).isTrue();
            assertThat(target.contains(mergedPath)).isTrue();
            assertThat(target.contains("/merged/assets/keep.txt")).isTrue();
            assertThat(target.contains("/merged/docs/readme.txt")).isTrue();
            assertThat(target.contains("/merged/assets/skip.txt")).isFalse();
            assertThat(readAsset(target.get("/merged/assets/keep.txt").getAsset())).isEqualTo("keep");
            assertThat(readAsset(target.get("/merged/docs/readme.txt").getAsset())).isEqualTo("readme");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void memoryMapArchiveResolvesNestedArchivesByTypeAndArchiveFormat() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Configuration configuration = configuration(new StubExtensionLoader(), executorService);
            InMemoryMemoryMapArchive archive = new InMemoryMemoryMapArchive(
                    "application", configuration, ArchiveFormat.ZIP);
            ArchivePath libraryPath = path("/WEB-INF/lib/module.jar");
            ArchivePath readmePath = path("/docs/readme.txt");
            archive.add(new TestAsset("library"), libraryPath);
            archive.add(new TestAsset("readme"), readmePath);

            MemoryMapArchive byStringPath = archive.getAsType(MemoryMapArchive.class, "/WEB-INF/lib/module.jar");
            MemoryMapArchive byArchivePathAndFormat = archive.getAsType(
                    MemoryMapArchive.class, libraryPath, ArchiveFormat.ZIP);
            Collection<MemoryMapArchive> byFilter = archive.getAsType(
                    MemoryMapArchive.class, path -> path.get().startsWith("/WEB-INF/lib/"));
            Collection<MemoryMapArchive> byFilterAndFormat = archive.getAsType(
                    MemoryMapArchive.class, path -> path.get().endsWith("readme.txt"), ArchiveFormat.ZIP);

            assertThat(byStringPath).isSameAs(archive);
            assertThat(byArchivePathAndFormat).isSameAs(archive);
            assertThat(byFilter).containsExactly(archive);
            assertThat(byFilterAndFormat).containsExactly(archive);
            assertThat(archive.getAsType(MemoryMapArchive.class, "/missing.jar")).isNull();
            assertThat(archive.getAsType(MemoryMapArchive.class, libraryPath, ArchiveFormat.TAR)).isNull();
            assertThat(archive.getAsType(
                    MemoryMapArchive.class, path -> path.get().endsWith("module.jar"), ArchiveFormat.TAR)).isEmpty();
        } finally {
            executorService.shutdownNow();
        }
    }

    private static Configuration configuration(ExtensionLoader extensionLoader, ExecutorService executorService) {
        return new ConfigurationBuilder()
                .extensionLoader(extensionLoader)
                .executorService(executorService)
                .classLoaders(Collections.singletonList(currentClassLoader()))
                .build();
    }

    private static ClassLoader currentClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? Shrinkwrap_spiTest.class.getClassLoader() : classLoader;
    }

    private static ArchivePath path(String path) {
        return new TestArchivePath(path);
    }

    private static ArchivePath path(String path, String child) {
        return path(path(path), child);
    }

    private static ArchivePath path(ArchivePath path, String child) {
        return path(path.get() + "/" + child);
    }

    private static ArchivePath path(ArchivePath parent, ArchivePath child) {
        return path(parent.get() + "/" + child.get());
    }

    private static String readAsset(Asset asset) throws IOException {
        try (InputStream inputStream = asset.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class SimpleConfigurable implements Configurable {
        private final Configuration configuration;

        private SimpleConfigurable(Configuration configuration) {
            this.configuration = Objects.requireNonNull(configuration, "configuration");
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public <TYPE extends Assignable> TYPE as(Class<TYPE> type) {
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            throw new IllegalArgumentException("Unsupported assignment type: " + type.getName());
        }
    }

    private static final class SimpleIdentifiable implements Identifiable {
        private String id;

        private SimpleIdentifiable(String id) {
            setId(id);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) throws IllegalArgumentException {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            this.id = id;
        }

        @Override
        public <TYPE extends Assignable> TYPE as(Class<TYPE> type) {
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            throw new IllegalArgumentException("Unsupported assignment type: " + type.getName());
        }
    }

    private static final class SimpleArchiveFormatAssociable implements ArchiveFormatAssociable {
        private final ArchiveFormat archiveFormat;

        private SimpleArchiveFormatAssociable(ArchiveFormat archiveFormat) {
            this.archiveFormat = Objects.requireNonNull(archiveFormat, "archiveFormat");
        }

        @Override
        public ArchiveFormat getArchiveFormat() {
            return archiveFormat;
        }
    }

    private static final class InMemoryMemoryMapArchive implements MemoryMapArchive,
            Configurable, Identifiable, ArchiveFormatAssociable {
        private final Map<ArchivePath, Node> content;
        private final Configuration configuration;
        private final ArchiveFormat archiveFormat;
        private String id;

        private InMemoryMemoryMapArchive(String id, Configuration configuration, ArchiveFormat archiveFormat) {
            this(id, configuration, archiveFormat, new LinkedHashMap<>());
        }

        private InMemoryMemoryMapArchive(String id, Configuration configuration, ArchiveFormat archiveFormat,
                Map<ArchivePath, Node> content) {
            setId(id);
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            this.archiveFormat = Objects.requireNonNull(archiveFormat, "archiveFormat");
            this.content = content;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) throws IllegalArgumentException {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("id must not be blank");
            }
            this.id = id;
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public ArchiveFormat getArchiveFormat() {
            return archiveFormat;
        }

        @Override
        public MemoryMapArchive add(Asset asset, ArchivePath target) throws IllegalArgumentException {
            requireAsset(asset);
            content.put(normalize(target), new SimpleNode(normalize(target), asset));
            return this;
        }

        @Override
        public MemoryMapArchive add(Asset asset, ArchivePath path, String name) throws IllegalArgumentException {
            return add(asset, path(path, name));
        }

        @Override
        public MemoryMapArchive add(Asset asset, String path, String name) throws IllegalArgumentException {
            return add(asset, path(path, name));
        }

        @Override
        public MemoryMapArchive add(NamedAsset asset) throws IllegalArgumentException {
            return add(asset, asset.getName());
        }

        @Override
        public MemoryMapArchive add(Asset asset, String target) throws IllegalArgumentException {
            return add(asset, path(target));
        }

        @Override
        public MemoryMapArchive addAsDirectory(String path) throws IllegalArgumentException {
            return addAsDirectory(path(path));
        }

        @Override
        public MemoryMapArchive addAsDirectories(String... paths) throws IllegalArgumentException {
            for (String path : paths) {
                addAsDirectory(path);
            }
            return this;
        }

        @Override
        public MemoryMapArchive addAsDirectory(ArchivePath path) throws IllegalArgumentException {
            content.put(normalize(path), new SimpleNode(normalize(path), null));
            return this;
        }

        @Override
        public MemoryMapArchive addAsDirectories(ArchivePath... paths) throws IllegalArgumentException {
            for (ArchivePath path : paths) {
                addAsDirectory(path);
            }
            return this;
        }

        @Override
        public MemoryMapArchive addHandlers(ArchiveEventHandler... handlers) {
            return this;
        }

        @Override
        public Node get(ArchivePath path) throws IllegalArgumentException {
            return content.get(normalize(path));
        }

        @Override
        public Node get(String path) throws IllegalArgumentException {
            return get(path(path));
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, String path) {
            return getAsType(type, path(path));
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, ArchivePath path) {
            if (contains(path) && type.isInstance(this)) {
                return type.cast(this);
            }
            return null;
        }

        @Override
        public <X extends Archive<X>> Collection<X> getAsType(Class<X> type, Filter<ArchivePath> filter) {
            if (!type.isInstance(this)) {
                return Collections.emptyList();
            }
            for (ArchivePath path : content.keySet()) {
                if (filter.include(path)) {
                    return Collections.singletonList(type.cast(this));
                }
            }
            return Collections.emptyList();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, String path, ArchiveFormat archiveFormat) {
            return archiveFormat == this.archiveFormat ? getAsType(type, path) : null;
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, ArchivePath path, ArchiveFormat archiveFormat) {
            return archiveFormat == this.archiveFormat ? getAsType(type, path) : null;
        }

        @Override
        public <X extends Archive<X>> Collection<X> getAsType(Class<X> type, Filter<ArchivePath> filter,
                ArchiveFormat archiveFormat) {
            return archiveFormat == this.archiveFormat ? getAsType(type, filter) : Collections.emptyList();
        }

        @Override
        public boolean contains(ArchivePath path) throws IllegalArgumentException {
            return content.containsKey(normalize(path));
        }

        @Override
        public boolean contains(String path) throws IllegalArgumentException {
            return contains(path(path));
        }

        @Override
        public Node delete(ArchivePath path) throws IllegalArgumentException {
            return content.remove(normalize(path));
        }

        @Override
        public Node delete(String path) throws IllegalArgumentException {
            return delete(path(path));
        }

        @Override
        public Map<ArchivePath, Node> getContent() {
            return Collections.unmodifiableMap(content);
        }

        @Override
        public Map<ArchivePath, Node> getContent(Filter<ArchivePath> filter) {
            Map<ArchivePath, Node> filteredContent = new LinkedHashMap<>();
            for (Map.Entry<ArchivePath, Node> entry : content.entrySet()) {
                if (filter.include(entry.getKey())) {
                    filteredContent.put(entry.getKey(), entry.getValue());
                }
            }
            return filteredContent;
        }

        @Override
        public MemoryMapArchive add(Archive<?> archive, ArchivePath path,
                Class<? extends StreamExporter> exporter) throws IllegalArgumentException {
            return add(new TestAsset(archive.toString()), path);
        }

        @Override
        public MemoryMapArchive add(Archive<?> archive, String path,
                Class<? extends StreamExporter> exporter) throws IllegalArgumentException {
            return add(archive, path(path), exporter);
        }

        @Override
        public MemoryMapArchive merge(Archive<?> archive) throws IllegalArgumentException {
            content.putAll(archive.getContent());
            return this;
        }

        @Override
        public MemoryMapArchive merge(Archive<?> archive, Filter<ArchivePath> filter) throws IllegalArgumentException {
            content.putAll(archive.getContent(filter));
            return this;
        }

        @Override
        public MemoryMapArchive merge(Archive<?> archive, ArchivePath path) throws IllegalArgumentException {
            for (Map.Entry<ArchivePath, Node> entry : archive.getContent().entrySet()) {
                ArchivePath targetPath = path(path, entry.getKey());
                content.put(targetPath, new SimpleNode(targetPath, entry.getValue().getAsset()));
            }
            return this;
        }

        @Override
        public MemoryMapArchive merge(Archive<?> archive, String path) throws IllegalArgumentException {
            return merge(archive, path(path));
        }

        @Override
        public MemoryMapArchive merge(Archive<?> archive, ArchivePath path,
                Filter<ArchivePath> filter) throws IllegalArgumentException {
            for (Map.Entry<ArchivePath, Node> entry : archive.getContent(filter).entrySet()) {
                ArchivePath targetPath = path(path, entry.getKey());
                content.put(targetPath, new SimpleNode(targetPath, entry.getValue().getAsset()));
            }
            return this;
        }

        @Override
        public MemoryMapArchive merge(Archive<?> archive, String path,
                Filter<ArchivePath> filter) throws IllegalArgumentException {
            return merge(archive, path(path), filter);
        }

        @Override
        public MemoryMapArchive move(ArchivePath source, ArchivePath target) throws IllegalArgumentException {
            Node removed = delete(source);
            if (removed == null) {
                throw new IllegalArgumentException("Source path does not exist: " + source.get());
            }
            content.put(normalize(target), new SimpleNode(normalize(target), removed.getAsset()));
            return this;
        }

        @Override
        public MemoryMapArchive move(String source, String target) throws IllegalArgumentException {
            return move(path(source), path(target));
        }

        @Override
        public String toString() {
            return getName() + content.keySet();
        }

        @Override
        public String toString(boolean verbose) {
            return verbose ? toString() : getName();
        }

        @Override
        public String toString(Formatter formatter) throws IllegalArgumentException {
            return formatter.format(this);
        }

        @Override
        public void writeTo(OutputStream outputStream, Formatter formatter) throws IllegalArgumentException {
            try {
                outputStream.write(toString(formatter).getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not write archive", exception);
            }
        }

        @Override
        public Archive<MemoryMapArchive> shallowCopy() {
            return new InMemoryMemoryMapArchive(id, configuration, archiveFormat, new LinkedHashMap<>(content));
        }

        @Override
        public <TYPE extends Assignable> TYPE as(Class<TYPE> type) {
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            return configuration.getExtensionLoader().load(type, this);
        }

        private static ArchivePath normalize(ArchivePath path) {
            if (path == null) {
                throw new IllegalArgumentException("path must not be null");
            }
            return path(path.get());
        }

        private static void requireAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("asset must not be null");
            }
        }
    }

    private static final class SimpleNode implements Node {
        private final ArchivePath path;
        private final Asset asset;

        private SimpleNode(ArchivePath path, Asset asset) {
            this.path = path;
            this.asset = asset;
        }

        @Override
        public Asset getAsset() {
            return asset;
        }

        @Override
        public Set<Node> getChildren() {
            return Collections.emptySet();
        }

        @Override
        public ArchivePath getPath() {
            return path;
        }
    }

    private static final class TestArchivePath implements ArchivePath {
        private final String path;

        private TestArchivePath(String path) {
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("path must not be blank");
            }
            String normalized = path.replace('\\', '/').replaceAll("/++", "/");
            this.path = normalized.startsWith("/") ? normalized : "/" + normalized;
        }

        @Override
        public String get() {
            return path;
        }

        @Override
        public ArchivePath getParent() {
            if ("/".equals(path)) {
                return null;
            }
            int parentSeparator = path.lastIndexOf('/');
            return parentSeparator <= 0 ? path("/") : path(path.substring(0, parentSeparator));
        }

        @Override
        public int compareTo(ArchivePath other) {
            return path.compareTo(other.get());
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ArchivePath)) {
                return false;
            }
            ArchivePath other = (ArchivePath) object;
            return path.equals(other.get());
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return path;
        }
    }

    private static class TestAsset implements Asset {
        private final String content;

        private TestAsset(String content) {
            this.content = content;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class TestNamedAsset extends TestAsset implements NamedAsset {
        private final String name;

        private TestNamedAsset(String name, String content) {
            super(content);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class StubExtensionLoader implements ExtensionLoader {
        private final Map<Class<?>, Class<?>> overrides = new LinkedHashMap<>();
        private final Map<Class<?>, ArchiveFormat> archiveFormats = new LinkedHashMap<>();

        @Override
        public <T extends Assignable> T load(Class<T> type, Archive<?> archive) {
            if (type.isInstance(archive)) {
                return type.cast(archive);
            }
            throw new IllegalArgumentException("Unsupported extension type: " + type.getName());
        }

        @Override
        public <T extends Assignable> StubExtensionLoader addOverride(
                Class<T> type, Class<? extends T> implementation) {
            overrides.put(type, implementation);
            return this;
        }

        private <T extends Archive<T>> StubExtensionLoader addArchiveFormat(
                Class<T> type, ArchiveFormat archiveFormat) {
            archiveFormats.put(type, archiveFormat);
            return this;
        }

        @Override
        public <T extends Assignable> String getExtensionFromExtensionMapping(Class<T> type) {
            Class<?> implementation = overrides.get(type);
            return implementation == null ? null : implementation.getName();
        }

        @Override
        public <T extends Archive<T>> ArchiveFormat getArchiveFormatFromExtensionMapping(Class<T> type) {
            return archiveFormats.get(type);
        }
    }
}
