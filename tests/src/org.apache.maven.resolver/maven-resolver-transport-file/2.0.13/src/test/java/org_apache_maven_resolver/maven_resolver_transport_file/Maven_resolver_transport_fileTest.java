/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_transport_file;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Maven_resolver_transport_fileTest {

    private static final URI ARTIFACT_URI = URI.create("org/example/demo/1.0/demo-1.0.txt");

    @TempDir
    Path temporaryDirectory;

    @Test
    void factoryCreatesFileTransporterAndRoundTripsStringContent() throws Exception {
        String content = """
                first line
                second line
                """;
        Path repositoryBase = temporaryDirectory.resolve("repository");
        Files.createDirectories(repositoryBase);
        FileTransporterFactory factory = new FileTransporterFactory().setPriority(42.5F);

        assertThat(FileTransporterFactory.NAME).isEqualTo("file");
        assertThat(factory.getPriority()).isEqualTo(42.5F);

        Transporter transporter = factory.newInstance(
                session(), repository("local", repositoryBase.toUri().toASCIIString()));
        try {
            RecordingTransportListener putListener = new RecordingTransportListener();
            transporter.put(new PutTask(ARTIFACT_URI).setDataString(content).setListener(putListener));

            Path storedArtifact = repositoryBase.resolve(ARTIFACT_URI.getPath());
            assertThat(storedArtifact).exists();
            assertThat(Files.readString(storedArtifact, StandardCharsets.UTF_8)).isEqualTo(content);
            assertThat(putListener.startedOffset).isZero();
            assertThat(putListener.startedLength).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
            assertThat(putListener.progressedBytes).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);

            transporter.peek(new PeekTask(ARTIFACT_URI));

            RecordingTransportListener getListener = new RecordingTransportListener();
            GetTask getTask = new GetTask(ARTIFACT_URI).setListener(getListener);
            transporter.get(getTask);

            assertThat(getTask.getDataString()).isEqualTo(content);
            assertThat(getTask.getDataBytes()).containsExactly(content.getBytes(StandardCharsets.UTF_8));
            assertThat(getListener.startedOffset).isZero();
            assertThat(getListener.startedLength).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
            assertThat(getListener.progressedBytes).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        } finally {
            transporter.close();
        }
    }

    @Test
    void putFromDataFileAndGetToDataFilePreservesBinaryContent() throws Exception {
        byte[] data = new byte[] {0, 1, 2, 3, 5, 8, 13, 21, -1};
        Path repositoryBase = temporaryDirectory.resolve("repository");
        Path sourceFile = temporaryDirectory.resolve("source.bin");
        Path targetFile = temporaryDirectory.resolve("target.bin");
        Files.createDirectories(repositoryBase);
        Files.write(sourceFile, data);

        Transporter transporter = newTransporter("local", repositoryBase.toUri().toASCIIString());
        try {
            PutTask putTask = new PutTask(ARTIFACT_URI).setDataFile(sourceFile.toFile());
            transporter.put(putTask);

            Path storedArtifact = repositoryBase.resolve(ARTIFACT_URI.getPath());
            assertThat(storedArtifact).exists();
            assertThat(Files.readAllBytes(storedArtifact)).containsExactly(data);

            GetTask getTask = new GetTask(ARTIFACT_URI).setDataFile(targetFile.toFile());
            transporter.get(getTask);

            assertThat(targetFile).exists();
            assertThat(Files.readAllBytes(targetFile)).containsExactly(data);
            assertThat(getTask.getDataBytes()).isEmpty();
            assertThat(getTask.getDataString()).isEmpty();
        } finally {
            transporter.close();
        }
    }

    @Test
    void missingResourcesAreClassifiedAsNotFoundAndIllegalPathsAreRejected() throws Exception {
        Path repositoryBase = temporaryDirectory.resolve("repository");
        Files.createDirectories(repositoryBase);

        Transporter transporter = newTransporter("local", repositoryBase.toUri().toASCIIString());
        try {
            Throwable missing = catchThrowable(() -> transporter.peek(new PeekTask(URI.create("missing.txt"))));
            assertThat(missing).isNotNull();
            assertThat(transporter.classify(missing)).isEqualTo(Transporter.ERROR_NOT_FOUND);
            assertThat(missing).hasMessageContaining("Could not locate");

            Throwable traversal = catchThrowable(() -> transporter.get(new GetTask(URI.create("../outside.txt"))));
            assertThat(traversal).isInstanceOf(IllegalArgumentException.class);
            assertThat(transporter.classify(traversal)).isEqualTo(Transporter.ERROR_OTHER);
            assertThat(traversal).hasMessageContaining("illegal resource path");
        } finally {
            transporter.close();
        }

        assertThatThrownBy(() -> transporter.peek(new PeekTask(ARTIFACT_URI)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transporter closed");
    }

    @Test
    void hardlinkRepositoryUrlCanMaterializeDownloadedFilesAsLinks() throws Exception {
        byte[] data = "linked artifact".getBytes(StandardCharsets.UTF_8);
        Path repositoryBase = temporaryDirectory.resolve("repository");
        Path artifact = repositoryBase.resolve(ARTIFACT_URI.getPath());
        Path target = temporaryDirectory.resolve("linked-target.txt");
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, data);

        Transporter transporter = newTransporter("hardlink", "hardlink+" + repositoryBase.toUri().toASCIIString());
        try {
            RecordingTransportListener listener = new RecordingTransportListener();
            transporter.get(new GetTask(ARTIFACT_URI).setDataFile(target.toFile()).setListener(listener));

            assertThat(target).exists();
            assertThat(Files.readAllBytes(target)).containsExactly(data);
            assertThat(Files.isSameFile(artifact, target)).isTrue();
            assertThat(listener.startedOffset).isZero();
            assertThat(listener.startedLength).isEqualTo(data.length);
            assertThat(listener.progressedBytes).isEqualTo(data.length);
        } finally {
            transporter.close();
        }
    }

    @Test
    void linkRepositoryUrlReadsContentIntoMemoryWhenNoTargetFileIsProvided() throws Exception {
        String content = "in-memory linked artifact";
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        Path repositoryBase = temporaryDirectory.resolve("repository");
        Path artifact = repositoryBase.resolve(ARTIFACT_URI.getPath());
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, data);

        Transporter transporter = newTransporter("hardlink", "hardlink+" + repositoryBase.toUri().toASCIIString());
        try {
            RecordingTransportListener listener = new RecordingTransportListener();
            GetTask getTask = new GetTask(ARTIFACT_URI).setListener(listener);
            transporter.get(getTask);

            assertThat(getTask.getDataString()).isEqualTo(content);
            assertThat(getTask.getDataBytes()).containsExactly(data);
            assertThat(listener.startedOffset).isZero();
            assertThat(listener.startedLength).isEqualTo(data.length);
            assertThat(listener.progressedBytes).isEqualTo(data.length);
        } finally {
            transporter.close();
        }
    }

    @Test
    void symlinkRepositoryUrlCanMaterializeDownloadedFilesAsSymbolicLinks() throws Exception {
        byte[] data = "symbolic artifact".getBytes(StandardCharsets.UTF_8);
        Path repositoryBase = temporaryDirectory.resolve("repository");
        Path artifact = repositoryBase.resolve(ARTIFACT_URI.getPath());
        Path target = temporaryDirectory.resolve("symlink-target.txt");
        Files.createDirectories(artifact.getParent());
        Files.write(artifact, data);

        Transporter transporter = newTransporter("symlink", "symlink+" + repositoryBase.toUri().toASCIIString());
        try {
            RecordingTransportListener listener = new RecordingTransportListener();
            transporter.get(new GetTask(ARTIFACT_URI).setDataFile(target.toFile()).setListener(listener));

            assertThat(target).exists();
            assertThat(Files.isSymbolicLink(target)).isTrue();
            assertThat(Files.readSymbolicLink(target)).isEqualTo(artifact);
            assertThat(Files.readAllBytes(target)).containsExactly(data);
            assertThat(listener.startedOffset).isZero();
            assertThat(listener.startedLength).isEqualTo(data.length);
            assertThat(listener.progressedBytes).isEqualTo(data.length);
        } finally {
            transporter.close();
        }
    }

    @Test
    void bundleRepositoryReadsJarEntriesAndRejectsWrites() throws Exception {
        Path archive = temporaryDirectory.resolve("repository.jar");
        URI zipUri = URI.create("jar:" + archive.toUri().toASCIIString());
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
            Path entryDirectory = zipFileSystem.getPath("nested");
            Files.createDirectories(entryDirectory);
            Files.writeString(entryDirectory.resolve("entry.txt"), "from archive", StandardCharsets.UTF_8);
        }

        Transporter transporter = newTransporter("bundle", "bundle:" + archive.toUri().toASCIIString());
        try {
            URI entryUri = URI.create("nested/entry.txt");
            transporter.peek(new PeekTask(entryUri));

            GetTask getTask = new GetTask(entryUri);
            transporter.get(getTask);
            assertThat(getTask.getDataString()).isEqualTo("from archive");

            assertThatThrownBy(() -> transporter.put(new PutTask(URI.create("nested/new.txt")).setDataString("new")))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Read only FileSystem");
        } finally {
            transporter.close();
        }
    }

    @Test
    void unsupportedRepositoryUrlReportsNoTransporter() {
        FileTransporterFactory factory = new FileTransporterFactory();
        RemoteRepository repository = repository("unsupported", "unknown://example.invalid/repository");

        assertThatThrownBy(() -> factory.newInstance(session(), repository))
                .isInstanceOf(NoTransporterException.class)
                .extracting("repository")
                .isEqualTo(repository);
    }

    private Transporter newTransporter(String id, String url) throws NoTransporterException {
        return new FileTransporterFactory().newInstance(session(), repository(id, url));
    }

    private static RepositorySystemSession session() {
        return new DefaultRepositorySystemSession();
    }

    private static RemoteRepository repository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }

    private static final class RecordingTransportListener extends TransportListener {
        private long startedOffset = -1;
        private long startedLength = -1;
        private long progressedBytes;

        @Override
        public void transportStarted(long dataOffset, long dataLength) {
            startedOffset = dataOffset;
            startedLength = dataLength;
        }

        @Override
        public void transportProgressed(ByteBuffer data) {
            progressedBytes += data.remaining();
        }
    }
}
