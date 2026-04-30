/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.smallrye.common.resource.JarFileResourceLoader;
import io.smallrye.common.resource.MemoryInputStream;
import io.smallrye.common.resource.MemoryResource;
import io.smallrye.common.resource.PathResource;
import io.smallrye.common.resource.PathResourceLoader;
import io.smallrye.common.resource.Resource;
import io.smallrye.common.resource.ResourceLoader;
import io.smallrye.common.resource.ResourceURLConnection;
import io.smallrye.common.resource.ResourceUtils;
import io.smallrye.common.resource.URLResource;
import io.smallrye.common.resource.URLResourceLoader;
import org.junit.jupiter.api.Test;

public class Smallrye_common_resourceTest {
    @Test
    void canonicalizesRelativePathsWithoutEscapingAboveTheResourceRoot() {
        assertThat(ResourceUtils.canonicalizeRelativePath("")).isEmpty();
        assertThat(ResourceUtils.canonicalizeRelativePath("/")).isEmpty();
        assertThat(ResourceUtils.canonicalizeRelativePath("./assets//images/./logo.png"))
                .isEqualTo("assets/images/logo.png");
        assertThat(ResourceUtils.canonicalizeRelativePath("assets/config/../data/./sample.txt"))
                .isEqualTo("assets/data/sample.txt");
        assertThat(ResourceUtils.canonicalizeRelativePath("../../outside.txt")).isEqualTo("outside.txt");
    }

    @Test
    void memoryResourceProvidesRepeatableStreamsBuffersCopiesAndUrls() throws IOException {
        byte[] content = bytes("smallrye resource\nline two");
        MemoryResource resource = new MemoryResource("folder/./sample.txt", content);

        assertThat(resource.pathName()).isEqualTo("folder/sample.txt");
        assertThat(resource.size()).isEqualTo(content.length);
        assertThat(resource.modifiedTime()).isNotNull();
        assertThat(resource.codeSigners()).isEmpty();
        assertThat(resource.isDirectory()).isFalse();
        assertThat(resource.asString(StandardCharsets.UTF_8)).isEqualTo("smallrye resource\nline two");
        assertThat(readRemaining(resource.asBuffer())).isEqualTo(content);
        assertThat(resource.asBuffer().isReadOnly()).isTrue();

        try (MemoryInputStream stream = resource.openStream()) {
            assertThat(stream.markSupported()).isTrue();
            assertThat(stream.available()).isEqualTo(content.length);
            assertThat(stream.read()).isEqualTo('s');
            stream.mark(100);
            assertThat(stream.skip(8)).isEqualTo(8);
            stream.reset();
            assertThat(stream.readAllBytes()).isEqualTo(bytes("mallrye resource\nline two"));
        }
        assertThatThrownBy(() -> {
            try (MemoryInputStream stream = resource.openStream()) {
                stream.close();
                stream.read();
            }
        }).isInstanceOf(IOException.class).hasMessage("Stream closed");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        assertThat(resource.copyTo(outputStream)).isEqualTo(content.length);
        assertThat(outputStream.toByteArray()).isEqualTo(content);

        ByteArrayOutputStream channelOutput = new ByteArrayOutputStream();
        assertThat(resource.copyTo(Channels.newChannel(channelOutput))).isEqualTo(content.length);
        assertThat(channelOutput.toByteArray()).isEqualTo(content);

        Path copiedFile = Files.createTempFile("smallrye-resource-memory", ".txt");
        try {
            assertThat(resource.copyTo(copiedFile)).isEqualTo(content.length);
            assertThat(Files.readAllBytes(copiedFile)).isEqualTo(content);
        } finally {
            Files.deleteIfExists(copiedFile);
        }

        URL url = resource.url();
        assertThat(url).isSameAs(resource.url());
        assertThat(url.getProtocol()).isEqualTo("memory");
        URLConnection connection = url.openConnection();
        assertThat(connection).isInstanceOf(ResourceURLConnection.class);
        ResourceURLConnection resourceConnection = (ResourceURLConnection) connection;
        assertThat(resourceConnection.resource()).isSameAs(resource);
        assertThat(resourceConnection.getContentLengthLong()).isEqualTo(content.length);
        assertThat(resourceConnection.getContentType()).isEqualTo("application/octet-stream");
        assertThat(resourceConnection.getLastModified()).isEqualTo(resource.modifiedTime().toEpochMilli());
        assertThat((byte[]) resourceConnection.getContent()).isEqualTo(content);
        assertThat(readRemaining((ByteBuffer) resourceConnection.getContent(ByteBuffer.class))).isEqualTo(content);
        assertThat(resourceConnection.getContent(Resource.class)).isSameAs(resource);
        try (InputStream stream = resourceConnection.getInputStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void pathResourceLoaderFindsFilesDirectoriesChildrenAndManifest() throws IOException {
        Path root = Files.createTempDirectory("smallrye-resource-path");
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path leaf = nested.resolve("leaf.txt");
        Files.writeString(leaf, "leaf content", StandardCharsets.UTF_8);
        Files.createDirectories(root.resolve("META-INF"));
        Files.writeString(root.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\r\nCreated-By: SmallRye resource test\r\n\r\n",
                StandardCharsets.UTF_8);

        PathResourceLoader loader = new PathResourceLoader(root);

        assertThat(loader.baseUrl().getProtocol()).isEqualTo("file");
        assertThat(loader.findResource("missing.txt")).isNull();
        assertThat(loader.manifest().getMainAttributes().getValue("Created-By"))
                .isEqualTo("SmallRye resource test");

        Resource fileResource = loader.findResource("nested/./leaf.txt");
        assertThat(fileResource).isInstanceOf(PathResource.class);
        assertThat(fileResource.pathName()).isEqualTo("nested/leaf.txt");
        assertThat(fileResource.isDirectory()).isFalse();
        assertThat(fileResource.size()).isEqualTo(bytes("leaf content").length);
        assertThat(fileResource.asString(StandardCharsets.UTF_8)).isEqualTo("leaf content");
        assertThat(fileResource.modifiedTime()).isNotNull();
        assertThat(Files.isSameFile(((PathResource) fileResource).path(), leaf)).isTrue();
        try (InputStream stream = fileResource.url().openStream()) {
            assertThat(stream.readAllBytes()).isEqualTo(bytes("leaf content"));
        }

        Resource directory = loader.findResource("nested");
        assertThat(directory).isNotNull();
        assertThat(directory.isDirectory()).isTrue();
        assertThat(resourcePathNames(directory.openDirectoryStream())).containsExactly("nested/leaf.txt");

        Resource childResource = loader.getChildLoader("nested").findResource("leaf.txt");
        assertThat(childResource).isNotNull();
        assertThat(childResource.asString(StandardCharsets.UTF_8)).isEqualTo("leaf content");
    }

    @Test
    void jarResourceLoaderReadsManifestFilesDirectoriesAndChildResources() throws IOException {
        Path jarPath = Files.createTempFile("smallrye-resource", ".jar");
        createSampleJar(jarPath);

        try (JarFileResourceLoader loader = new JarFileResourceLoader(jarPath)) {
            assertThat(loader.baseUrl().getProtocol()).isEqualTo("file");
            assertThat(loader.manifest().getMainAttributes().getValue("Implementation-Title"))
                    .isEqualTo("smallrye-resource-test");
            assertThat(loader.findResource("absent.txt")).isNull();

            Resource file = loader.findResource("assets/./data.txt");
            assertThat(file.pathName()).isEqualTo("assets/data.txt");
            assertThat(file.isDirectory()).isFalse();
            assertThat(file.size()).isEqualTo(bytes("jar data").length);
            assertThat(file.asString(StandardCharsets.UTF_8)).isEqualTo("jar data");
            assertThat(file.codeSigners()).isEmpty();
            assertThat(file.url().getProtocol()).isEqualTo("jar");
            assertThat(((ResourceURLConnection) file.url().openConnection()).resource()).isSameAs(file);

            Resource directory = loader.findResource("assets");
            assertThat(directory).isNotNull();
            assertThat(directory.isDirectory()).isTrue();
            assertThat(resourcePathNames(directory.openDirectoryStream()))
                    .containsExactly("assets/data.txt", "assets/nested");

            Resource child = loader.getChildLoader("assets").findResource("nested/item.txt");
            assertThat(child).isNotNull();
            assertThat(child.asString(StandardCharsets.UTF_8)).isEqualTo("nested item");
        }
    }

    @Test
    void jarResourceLoaderCanReadAnArchiveSuppliedAsAResource() throws IOException {
        Path jarPath = Files.createTempFile("smallrye-resource-memory-archive", ".jar");
        createSampleJar(jarPath);
        MemoryResource archiveResource = new MemoryResource("archives/sample.jar", Files.readAllBytes(jarPath));

        try (JarFileResourceLoader loader = new JarFileResourceLoader(archiveResource)) {
            Resource file = loader.findResource("assets/data.txt");

            assertThat(file).isNotNull();
            assertThat(loader.baseUrl()).isSameAs(archiveResource.url());
            assertThat(file.asString(StandardCharsets.UTF_8)).isEqualTo("jar data");
            assertThat(loader.manifest().getMainAttributes().getValue("Implementation-Title"))
                    .isEqualTo("smallrye-resource-test");
        }
    }

    @Test
    void jarResourceLoaderResolvesRuntimeVersionedEntriesFromMultiReleaseArchives() throws IOException {
        Path jarPath = Files.createTempFile("smallrye-resource-multi-release", ".jar");
        createMultiReleaseJar(jarPath);

        try (JarFileResourceLoader loader = new JarFileResourceLoader(jarPath)) {
            Resource resource = loader.findResource("versioned/data.txt");

            assertThat(resource).isNotNull();
            assertThat(resource.pathName()).isEqualTo("versioned/data.txt");
            assertThat(resource.asString(StandardCharsets.UTF_8)).isEqualTo("runtime-specific data");
        }
    }

    @Test
    void urlResourceLoaderWrapsFileUrlResources() throws IOException {
        Path root = Files.createTempDirectory("smallrye-resource-url");
        Path nested = Files.createDirectories(root.resolve("nested"));
        Files.writeString(nested.resolve("from-url.txt"), "url content", StandardCharsets.UTF_8);

        URLResourceLoader loader = new URLResourceLoader(root.toUri().toURL());
        Resource resource = loader.findResource("nested/./from-url.txt");

        assertThat(loader.baseUrl()).isEqualTo(root.toUri().toURL());
        assertThat(resource).isInstanceOf(URLResource.class);
        assertThat(resource.pathName()).isEqualTo("nested/from-url.txt");
        assertThat(resource.url().getProtocol()).isEqualTo("file");
        assertThat(resource.size()).isEqualTo(bytes("url content").length);
        assertThat(resource.modifiedTime()).isNotNull();
        assertThat(resource.asString(StandardCharsets.UTF_8)).isEqualTo("url content");
    }

    @Test
    void resourceReadStreamTransformsContentClosesStreamAndUnwrapsIoFailures() throws IOException {
        MemoryResource resource = new MemoryResource("stream/data.txt", bytes("stream content"));
        MemoryInputStream[] openedStreams = new MemoryInputStream[1];

        String value = resource.readStream(inputStream -> {
            openedStreams[0] = (MemoryInputStream) inputStream;
            try {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertThat(value).isEqualTo("STREAM CONTENT");
        assertThatThrownBy(() -> openedStreams[0].read()).isInstanceOf(IOException.class)
                .hasMessage("Stream closed");
        assertThatThrownBy(() -> resource.readStream(inputStream -> {
            throw new UncheckedIOException(new IOException("reader failed"));
        })).isInstanceOf(IOException.class).hasMessage("reader failed");
    }

    @Test
    void emptyResourceLoaderAndDefaultMethodsAreSafeNoops() throws IOException {
        ResourceLoader emptyLoader = ResourceLoader.EMPTY;

        assertThat(emptyLoader.findResource("anything.txt")).isNull();
        assertThat(emptyLoader.getChildLoader("")).isSameAs(emptyLoader);
        assertThat(emptyLoader.getChildLoader("child").findResource("anything.txt")).isNull();
        assertThat(emptyLoader.manifest()).isNull();
        assertThatThrownBy(emptyLoader::baseUrl).isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Base URL is not supported by this resource loader");
        assertThatCode(emptyLoader::release).doesNotThrowAnyException();
        assertThatCode(emptyLoader::close).doesNotThrowAnyException();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readRemaining(ByteBuffer buffer) {
        byte[] content = new byte[buffer.remaining()];
        buffer.get(content);
        return content;
    }

    private static List<String> resourcePathNames(DirectoryStream<Resource> directoryStream) throws IOException {
        try (DirectoryStream<Resource> stream = directoryStream) {
            List<String> names = new ArrayList<>();
            for (Resource resource : stream) {
                names.add(resource.pathName());
            }
            return names;
        }
    }

    private static void createSampleJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Implementation-Title", "smallrye-resource-test");

        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            putDirectory(outputStream, "assets/");
            putEntry(outputStream, "assets/data.txt", "jar data");
            putDirectory(outputStream, "assets/nested/");
            putEntry(outputStream, "assets/nested/item.txt", "nested item");
        }
    }

    private static void createMultiReleaseJar(Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Multi-Release", "true");

        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            putEntry(outputStream, "versioned/data.txt", "base data");
            putEntry(outputStream, "META-INF/versions/9/versioned/data.txt", "runtime-specific data");
        }
    }

    private static void putDirectory(JarOutputStream outputStream, String name) throws IOException {
        JarEntry entry = new JarEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.closeEntry();
    }

    private static void putEntry(JarOutputStream outputStream, String name, String content) throws IOException {
        JarEntry entry = new JarEntry(name);
        outputStream.putNextEntry(entry);
        outputStream.write(bytes(content));
        outputStream.closeEntry();
    }
}
