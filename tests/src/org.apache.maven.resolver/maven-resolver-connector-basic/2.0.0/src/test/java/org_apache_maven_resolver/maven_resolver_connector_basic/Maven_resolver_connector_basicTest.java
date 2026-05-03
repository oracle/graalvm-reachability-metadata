/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_connector_basic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.checksums.ProvidedChecksumsSource;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithm;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicy;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.transfer.TransferResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_resolver_connector_basicTest {
    @TempDir
    Path tempDirectory;

    @Test
    void downloadsArtifactsMetadataAndPerformsExistenceChecks() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter();
        Artifact artifact = artifact();
        Metadata metadata = metadata();
        transporter.putBytes(
                SimpleRepositoryLayout.locationFor(artifact), "artifact-content".getBytes(StandardCharsets.UTF_8));
        transporter.putBytes(
                SimpleRepositoryLayout.locationFor(metadata), "metadata-content".getBytes(StandardCharsets.UTF_8));
        RecordingTransferListener listener = new RecordingTransferListener();
        AtomicInteger providedChecksumsCalls = new AtomicInteger();
        ProvidedChecksumsSource checksumSource = (session, download, repository, algorithms) -> {
            providedChecksumsCalls.incrementAndGet();
            assertThat(repository.getId()).isEqualTo("central");
            assertThat(algorithms).isEmpty();
            return Collections.emptyMap();
        };

        RepositoryConnector connector = newConnector(transporter, Map.of("test", checksumSource));
        try {
            Path artifactTarget = tempDirectory.resolve("downloaded-artifact.jar");
            Path metadataTarget = tempDirectory.resolve("downloaded-metadata.xml");
            Path existenceTarget = tempDirectory.resolve("existence-check.jar");
            ArtifactDownload artifactDownload = new ArtifactDownload(artifact, "resolve", artifactTarget.toFile(), null)
                    .setListener(listener);
            MetadataDownload metadataDownload = new MetadataDownload(metadata, "resolve", metadataTarget.toFile(), null)
                    .setListener(listener);
            ArtifactDownload existenceCheck = new ArtifactDownload(artifact, "resolve", existenceTarget.toFile(), null)
                    .setExistenceCheck(true)
                    .setListener(listener);

            connector.get(List.of(artifactDownload, existenceCheck), List.of(metadataDownload));

            assertThat(Files.readString(artifactTarget)).isEqualTo("artifact-content");
            assertThat(Files.readString(metadataTarget)).isEqualTo("metadata-content");
            assertThat(existenceTarget).doesNotExist();
            assertThat(artifactDownload.getException()).isNull();
            assertThat(metadataDownload.getException()).isNull();
            assertThat(existenceCheck.getException()).isNull();
            assertThat(transporter.getLocations()).containsExactly(
                    SimpleRepositoryLayout.locationFor(metadata),
                    SimpleRepositoryLayout.locationFor(artifact));
            assertThat(transporter.peekLocations()).containsExactly(SimpleRepositoryLayout.locationFor(artifact));
            assertThat(providedChecksumsCalls).hasValue(2);
            assertThat(listener.events()).anySatisfy(event -> {
                assertThat(event.getType()).isEqualTo(TransferEvent.EventType.SUCCEEDED);
                assertThat(event.getRequestType()).isEqualTo(TransferEvent.RequestType.GET);
                assertThat(event.getResource().getRepositoryId()).isEqualTo("central");
            });
            assertThat(listener.events()).anySatisfy(event -> {
                assertThat(event.getType()).isEqualTo(TransferEvent.EventType.SUCCEEDED);
                assertThat(event.getRequestType()).isEqualTo(TransferEvent.RequestType.GET_EXISTENCE);
            });
        } finally {
            connector.close();
        }
    }

    @Test
    void uploadsArtifactsAndMetadataToTransportLocations() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter();
        Artifact artifact = artifact();
        Metadata metadata = metadata();
        Path artifactSource = tempDirectory.resolve("artifact.jar");
        Path metadataSource = tempDirectory.resolve("maven-metadata.xml");
        Files.writeString(artifactSource, "uploaded-artifact");
        Files.writeString(metadataSource, "uploaded-metadata");
        RecordingTransferListener listener = new RecordingTransferListener();

        RepositoryConnector connector = newConnector(transporter, Collections.emptyMap());
        try {
            ArtifactUpload artifactUpload = new ArtifactUpload(artifact, artifactSource.toFile()).setListener(listener);
            MetadataUpload metadataUpload = new MetadataUpload(metadata, metadataSource.toFile()).setListener(listener);

            connector.put(List.of(artifactUpload), List.of(metadataUpload));

            assertThat(artifactUpload.getException()).isNull();
            assertThat(metadataUpload.getException()).isNull();
            assertThat(transporter.putLocations()).containsExactly(
                    SimpleRepositoryLayout.locationFor(artifact),
                    SimpleRepositoryLayout.locationFor(metadata));
            assertThat(transporter.content(SimpleRepositoryLayout.locationFor(artifact)))
                    .isEqualTo("uploaded-artifact");
            assertThat(transporter.content(SimpleRepositoryLayout.locationFor(metadata)))
                    .isEqualTo("uploaded-metadata");
            assertThat(listener.events()).anySatisfy(event -> {
                assertThat(event.getType()).isEqualTo(TransferEvent.EventType.SUCCEEDED);
                assertThat(event.getRequestType()).isEqualTo(TransferEvent.RequestType.PUT);
                assertThat(event.getResource().getRepositoryUrl()).isEqualTo(repository().getUrl() + "/");
            });
        } finally {
            connector.close();
        }
    }

    @Test
    void uploadsTransformedArtifactDataWhenFileTransformerIsConfigured() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter();
        Artifact artifact = artifact();
        Path artifactSource = tempDirectory.resolve("transformable-artifact.jar");
        Files.writeString(artifactSource, "original-artifact");
        AtomicBoolean transformed = new AtomicBoolean();
        FileTransformer transformer = new FileTransformer() {
            @Override
            public Artifact transformArtifact(Artifact artifact) {
                return artifact;
            }

            @Override
            public InputStream transformData(File file) throws IOException {
                transformed.set(true);
                String originalContent = Files.readString(file.toPath());
                String transformedContent = "transformed:" + originalContent.toUpperCase(Locale.ROOT);
                return new ByteArrayInputStream(transformedContent.getBytes(StandardCharsets.UTF_8));
            }
        };

        RepositoryConnector connector = newConnector(transporter, Collections.emptyMap());
        try {
            ArtifactUpload artifactUpload = new ArtifactUpload(artifact, artifactSource.toFile(), transformer);

            connector.put(List.of(artifactUpload), null);

            assertThat(artifactUpload.getException()).isNull();
            assertThat(transformed).isTrue();
            assertThat(transporter.putLocations()).containsExactly(SimpleRepositoryLayout.locationFor(artifact));
            assertThat(transporter.content(SimpleRepositoryLayout.locationFor(artifact)))
                    .isEqualTo("transformed:ORIGINAL-ARTIFACT");
            assertThat(Files.readString(artifactSource)).isEqualTo("original-artifact");
        } finally {
            connector.close();
        }
    }

    @Test
    void uploadsGeneratedChecksumsWhenLayoutProvidesChecksumLocations() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter();
        Artifact artifact = artifact();
        Metadata metadata = metadata();
        Path artifactSource = tempDirectory.resolve("checksummed-artifact.jar");
        Path metadataSource = tempDirectory.resolve("checksummed-metadata.xml");
        Files.writeString(artifactSource, "artifact-payload");
        Files.writeString(metadataSource, "metadata-payload");

        RepositoryConnector connector = configuredFactory(
                        (session, repository) -> transporter,
                        (session, repository) -> new SimpleRepositoryLayout(
                                List.of(LengthChecksumAlgorithmFactory.INSTANCE)),
                        Collections.emptyMap())
                .newInstance(session(), repository());
        try {
            ArtifactUpload artifactUpload = new ArtifactUpload(artifact, artifactSource.toFile());
            MetadataUpload metadataUpload = new MetadataUpload(metadata, metadataSource.toFile());

            connector.put(List.of(artifactUpload), List.of(metadataUpload));

            URI artifactLocation = SimpleRepositoryLayout.locationFor(artifact);
            URI artifactChecksumLocation = SimpleRepositoryLayout.checksumLocationFor(
                    artifactLocation, LengthChecksumAlgorithmFactory.INSTANCE);
            URI metadataLocation = SimpleRepositoryLayout.locationFor(metadata);
            URI metadataChecksumLocation = SimpleRepositoryLayout.checksumLocationFor(
                    metadataLocation, LengthChecksumAlgorithmFactory.INSTANCE);
            assertThat(artifactUpload.getException()).isNull();
            assertThat(metadataUpload.getException()).isNull();
            assertThat(transporter.putLocations()).containsExactly(
                    artifactLocation,
                    artifactChecksumLocation,
                    metadataLocation,
                    metadataChecksumLocation);
            assertThat(transporter.content(artifactChecksumLocation))
                    .isEqualTo(Long.toString(Files.size(artifactSource)));
            assertThat(transporter.content(metadataChecksumLocation))
                    .isEqualTo(Long.toString(Files.size(metadataSource)));
        } finally {
            connector.close();
        }
    }

    @Test
    void transportNotFoundIsReportedOnArtifactDownload() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter();
        ArtifactDownload download = new ArtifactDownload(
                artifact(), "resolve", tempDirectory.resolve("missing.jar").toFile(), null)
                .setListener(new RecordingTransferListener());

        RepositoryConnector connector = newConnector(transporter, Collections.emptyMap());
        try {
            connector.get(List.of(download), null);

            assertThat(download.getException()).isInstanceOf(ArtifactNotFoundException.class);
            assertThat(download.getException().getRepository()).isEqualTo(repository());
            assertThat(download.getException().getArtifact()).isEqualTo(artifact());
        } finally {
            connector.close();
        }
    }

    @Test
    void connectorClosesTransporterAndRejectsFurtherTransfers() throws Exception {
        InMemoryTransporter transporter = new InMemoryTransporter();
        RepositoryConnector connector = newConnector(transporter, Collections.emptyMap());

        assertThat(connector.toString()).isEqualTo(repository().toString());

        connector.close();
        connector.close();

        assertThat(transporter.closeCalls()).hasValue(1);
        assertThatThrownBy(() -> connector.get(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connector already closed");
    }

    @Test
    void factoryConfigurationIsFluentAndWrapsProviderFailures() {
        BasicRepositoryConnectorFactory factory = configuredFactory(new InMemoryTransporter(), Collections.emptyMap());

        assertThat(factory.setPriority(3.5f)).isSameAs(factory);
        assertThat(factory.getPriority()).isEqualTo(3.5f);
        assertThatNullPointerException().isThrownBy(() -> factory.setTransporterProvider(null));
        assertThatNullPointerException().isThrownBy(() -> factory.setRepositoryLayoutProvider(null));
        assertThatNullPointerException().isThrownBy(() -> factory.setChecksumPolicyProvider(null));
        assertThatNullPointerException().isThrownBy(() -> factory.setFileProcessor(null));
        assertThatNullPointerException().isThrownBy(() -> factory.setProvidedChecksumSources(null));

        RepositoryLayoutProvider failingLayoutProvider = (session, remoteRepository) -> {
            throw new NoRepositoryLayoutException(remoteRepository, "unsupported layout");
        };
        BasicRepositoryConnectorFactory layoutFailureFactory = configuredFactory(
                (session, remoteRepository) -> new InMemoryTransporter(),
                failingLayoutProvider,
                Collections.emptyMap());
        assertThatExceptionOfType(NoRepositoryConnectorException.class)
                .isThrownBy(() -> layoutFailureFactory.newInstance(session(), repository()))
                .withMessageContaining("unsupported layout")
                .withCauseInstanceOf(NoRepositoryLayoutException.class);

        TransporterProvider failingTransporterProvider = (session, remoteRepository) -> {
            throw new NoTransporterException(remoteRepository, "unsupported transport");
        };
        BasicRepositoryConnectorFactory transportFailureFactory = configuredFactory(
                failingTransporterProvider,
                (session, remoteRepository) -> new SimpleRepositoryLayout(),
                Collections.emptyMap());
        assertThatExceptionOfType(NoRepositoryConnectorException.class)
                .isThrownBy(() -> transportFailureFactory.newInstance(session(), repository()))
                .withMessageContaining("unsupported transport")
                .withCauseInstanceOf(NoTransporterException.class);
    }

    private static RepositoryConnector newConnector(
            InMemoryTransporter transporter, Map<String, ProvidedChecksumsSource> checksumSources)
            throws NoRepositoryConnectorException {
        return configuredFactory(transporter, checksumSources).newInstance(session(), repository());
    }

    private static BasicRepositoryConnectorFactory configuredFactory(
            InMemoryTransporter transporter, Map<String, ProvidedChecksumsSource> checksumSources) {
        return configuredFactory((session, repository) -> transporter,
                (session, repository) -> new SimpleRepositoryLayout(), checksumSources);
    }

    private static BasicRepositoryConnectorFactory configuredFactory(
            TransporterProvider transporterProvider,
            RepositoryLayoutProvider layoutProvider,
            Map<String, ProvidedChecksumsSource> checksumSources) {
        return new BasicRepositoryConnectorFactory()
                .setTransporterProvider(transporterProvider)
                .setRepositoryLayoutProvider(layoutProvider)
                .setChecksumPolicyProvider(new NoChecksumPolicyProvider())
                .setFileProcessor(new NioFileProcessor())
                .setProvidedChecksumSources(checksumSources);
    }

    private static RepositorySystemSession session() {
        return new DefaultRepositorySystemSession()
                .setConfigProperty("aether.connector.basic.threads", Integer.valueOf(1))
                .setConfigProperty("aether.connector.basic.parallelPut", Boolean.FALSE);
    }

    private static RemoteRepository repository() {
        return new RemoteRepository.Builder("central", "default", "https://repo.example.test/repository").build();
    }

    private static Artifact artifact() {
        return new DefaultArtifact("com.acme", "demo", "", "jar", "1.0");
    }

    private static Metadata metadata() {
        return new DefaultMetadata("com.acme", "demo", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
    }

    private static final class SimpleRepositoryLayout implements RepositoryLayout {
        private final List<ChecksumAlgorithmFactory> checksumAlgorithmFactories;

        SimpleRepositoryLayout() {
            this(Collections.emptyList());
        }

        SimpleRepositoryLayout(List<ChecksumAlgorithmFactory> checksumAlgorithmFactories) {
            this.checksumAlgorithmFactories = List.copyOf(checksumAlgorithmFactories);
        }

        static URI locationFor(Artifact artifact) {
            return URI.create("artifacts/"
                    + artifact.getGroupId().replace('.', '/') + "/"
                    + artifact.getArtifactId() + "/"
                    + artifact.getVersion() + "/"
                    + artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getExtension());
        }

        static URI locationFor(Metadata metadata) {
            StringBuilder path = new StringBuilder("metadata/")
                    .append(metadata.getGroupId().replace('.', '/'));
            if (!metadata.getArtifactId().isEmpty()) {
                path.append('/').append(metadata.getArtifactId());
            }
            if (!metadata.getVersion().isEmpty()) {
                path.append('/').append(metadata.getVersion());
            }
            return URI.create(path.append('/').append(metadata.getType()).toString());
        }

        static URI checksumLocationFor(URI location, ChecksumAlgorithmFactory checksumAlgorithmFactory) {
            return URI.create(location + "." + checksumAlgorithmFactory.getFileExtension());
        }

        @Override
        public List<ChecksumAlgorithmFactory> getChecksumAlgorithmFactories() {
            return checksumAlgorithmFactories;
        }

        @Override
        public boolean hasChecksums(Artifact artifact) {
            return !checksumAlgorithmFactories.isEmpty();
        }

        @Override
        public URI getLocation(Artifact artifact, boolean upload) {
            return locationFor(artifact);
        }

        @Override
        public URI getLocation(Metadata metadata, boolean upload) {
            return locationFor(metadata);
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations(Artifact artifact, boolean upload, URI location) {
            return checksumLocationsFor(location);
        }

        @Override
        public List<ChecksumLocation> getChecksumLocations(Metadata metadata, boolean upload, URI location) {
            return checksumLocationsFor(location);
        }

        private List<ChecksumLocation> checksumLocationsFor(URI location) {
            return checksumAlgorithmFactories.stream()
                    .map(factory -> ChecksumLocation.forLocation(location, factory))
                    .toList();
        }
    }

    private static final class LengthChecksumAlgorithmFactory implements ChecksumAlgorithmFactory {
        private static final LengthChecksumAlgorithmFactory INSTANCE = new LengthChecksumAlgorithmFactory();

        @Override
        public String getName() {
            return "length";
        }

        @Override
        public String getFileExtension() {
            return "len";
        }

        @Override
        public ChecksumAlgorithm getAlgorithm() {
            return new LengthChecksumAlgorithm();
        }
    }

    private static final class LengthChecksumAlgorithm implements ChecksumAlgorithm {
        private long byteCount;

        @Override
        public void update(ByteBuffer buffer) {
            byteCount += buffer.remaining();
        }

        @Override
        public String checksum() {
            return Long.toString(byteCount);
        }
    }

    private static final class InMemoryTransporter implements Transporter {
        private final Map<String, byte[]> contents = new ConcurrentHashMap<>();
        private final List<URI> getLocations = new ArrayList<>();
        private final List<URI> putLocations = new ArrayList<>();
        private final List<URI> peekLocations = new ArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger closeCalls = new AtomicInteger();

        void putBytes(URI location, byte[] bytes) {
            contents.put(location.toString(), bytes.clone());
        }

        String content(URI location) {
            return new String(contents.get(location.toString()), StandardCharsets.UTF_8);
        }

        List<URI> getLocations() {
            return List.copyOf(getLocations);
        }

        List<URI> putLocations() {
            return List.copyOf(putLocations);
        }

        List<URI> peekLocations() {
            return List.copyOf(peekLocations);
        }

        AtomicInteger closeCalls() {
            return closeCalls;
        }

        @Override
        public int classify(Throwable error) {
            return error instanceof FileNotFoundException ? ERROR_NOT_FOUND : ERROR_OTHER;
        }

        @Override
        public void peek(PeekTask task) throws Exception {
            failIfClosed();
            peekLocations.add(task.getLocation());
            if (!contents.containsKey(task.getLocation().toString())) {
                throw new FileNotFoundException(task.getLocation().toString());
            }
        }

        @Override
        public void get(GetTask task) throws Exception {
            failIfClosed();
            getLocations.add(task.getLocation());
            byte[] bytes = contents.get(task.getLocation().toString());
            if (bytes == null) {
                throw new FileNotFoundException(task.getLocation().toString());
            }
            notifyProgress(task.getListener(), bytes);
            try (OutputStream output = task.newOutputStream()) {
                output.write(bytes);
            }
        }

        @Override
        public void put(PutTask task) throws Exception {
            failIfClosed();
            putLocations.add(task.getLocation());
            byte[] bytes;
            try (InputStream input = task.newInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                input.transferTo(output);
                bytes = output.toByteArray();
            }
            notifyProgress(task.getListener(), bytes);
            contents.put(task.getLocation().toString(), bytes);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                closeCalls.incrementAndGet();
            }
        }

        private void failIfClosed() throws IOException {
            if (closed.get()) {
                throw new IOException("transporter is closed");
            }
        }

        private void notifyProgress(TransportListener listener, byte[] bytes) throws Exception {
            if (listener != null) {
                listener.transportStarted(0L, bytes.length);
                listener.transportProgressed(ByteBuffer.wrap(bytes));
            }
        }
    }

    private static final class NoChecksumPolicyProvider implements ChecksumPolicyProvider {
        @Override
        public ChecksumPolicy newChecksumPolicy(
                RepositorySystemSession session,
                RemoteRepository repository,
                TransferResource resource,
                String policy) {
            return null;
        }

        @Override
        public String getEffectiveChecksumPolicy(
                RepositorySystemSession session, String policy1, String policy2) {
            return policy1 != null ? policy1 : policy2;
        }
    }

    private static final class NioFileProcessor implements FileProcessor {
        @Override
        public boolean mkdirs(File directory) {
            try {
                Files.createDirectories(directory.toPath());
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public void write(File target, String data) throws IOException {
            createParentDirectories(target);
            Files.writeString(target.toPath(), data);
        }

        @Override
        public void write(File target, InputStream data) throws IOException {
            createParentDirectories(target);
            Files.copy(data, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void move(File source, File target) throws IOException {
            createParentDirectories(target);
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void copy(File source, File target) throws IOException {
            createParentDirectories(target);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public long copy(File source, File target, ProgressListener listener) throws IOException {
            createParentDirectories(target);
            long bytes = Files.size(source.toPath());
            try (InputStream input = Files.newInputStream(source.toPath());
                    OutputStream output = Files.newOutputStream(target.toPath())) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    if (listener != null && read > 0) {
                        listener.progressed(ByteBuffer.wrap(buffer, 0, read));
                    }
                }
            }
            return bytes;
        }

        @Override
        public String readChecksum(File checksumFile) throws IOException {
            return Files.readString(checksumFile.toPath()).trim();
        }

        @Override
        public void writeChecksum(File checksumFile, String checksum) throws IOException {
            write(checksumFile, checksum);
        }

        private static void createParentDirectories(File file) throws IOException {
            Path parent = file.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
    }

    private static final class RecordingTransferListener implements TransferListener {
        private final List<TransferEvent> events = new ArrayList<>();

        List<TransferEvent> events() {
            return List.copyOf(events);
        }

        @Override
        public void transferInitiated(TransferEvent event) {
            events.add(event);
        }

        @Override
        public void transferStarted(TransferEvent event) {
            events.add(event);
        }

        @Override
        public void transferProgressed(TransferEvent event) {
            events.add(event);
        }

        @Override
        public void transferCorrupted(TransferEvent event) {
            events.add(event);
        }

        @Override
        public void transferSucceeded(TransferEvent event) {
            events.add(event);
        }

        @Override
        public void transferFailed(TransferEvent event) {
            events.add(event);
        }
    }
}
