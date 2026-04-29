/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_aether.aether_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.metadata.Metadata;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.spi.connector.ArtifactDownload;
import org.sonatype.aether.spi.connector.ArtifactTransfer;
import org.sonatype.aether.spi.connector.ArtifactUpload;
import org.sonatype.aether.spi.connector.MetadataDownload;
import org.sonatype.aether.spi.connector.MetadataTransfer;
import org.sonatype.aether.spi.connector.MetadataUpload;
import org.sonatype.aether.spi.connector.RepositoryConnector;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.spi.connector.Transfer;
import org.sonatype.aether.spi.connector.Transfer.State;
import org.sonatype.aether.spi.io.FileProcessor;
import org.sonatype.aether.spi.locator.Service;
import org.sonatype.aether.spi.locator.ServiceLocator;
import org.sonatype.aether.spi.log.Logger;
import org.sonatype.aether.spi.log.NullLogger;
import org.sonatype.aether.transfer.ArtifactTransferException;
import org.sonatype.aether.transfer.MetadataTransferException;
import org.sonatype.aether.transfer.NoRepositoryConnectorException;

public class Aether_spiTest {
    @TempDir
    File tempDirectory;

    @Test
    void artifactDownloadTracksTransferStateContextsAndRepositoryManagerDetails() {
        SimpleArtifact artifact = new SimpleArtifact("org.example", "demo", "1.0.0", "jar", "");
        File targetFile = new File(tempDirectory, "demo.jar");
        RemoteRepository central = new RemoteRepository("central", "default", "https://repo.example.org/maven2");
        RemoteRepository snapshots = new RemoteRepository("snapshots", "default", "https://repo.example.org/snapshots");
        ArtifactTransferException failure = new ArtifactTransferException(artifact, central, "offline");

        ArtifactDownload download = new ArtifactDownload(artifact, "compile", targetFile, "fail");

        assertThat(download.getArtifact()).isSameAs(artifact);
        assertThat(download.getFile()).isEqualTo(targetFile);
        assertThat(download.getRequestContext()).isEqualTo("compile");
        assertThat(download.getChecksumPolicy()).isEqualTo("fail");
        assertThat(download.getSupportedContexts()).containsExactly("compile");
        assertThat(download.getRepositories()).isEmpty();
        assertThat(download.getState()).isEqualTo(State.NEW);

        assertThat(download.setExistenceCheck(true)).isSameAs(download);
        assertThat(download.isExistenceCheck()).isTrue();
        assertThat(download.setSupportedContexts(Arrays.asList("runtime", "plugin"))).isSameAs(download);
        assertThat(download.getSupportedContexts()).containsExactly("runtime", "plugin");

        download.setSupportedContexts(Collections.<String>emptyList());
        assertThat(download.getSupportedContexts()).containsExactly("compile");

        download.setState(State.ACTIVE);
        download.setRequestContext("plugin");
        assertThat(download.getRequestContext()).isEqualTo("plugin");
        assertThat(download.getSupportedContexts()).containsExactly("compile");

        assertThat(download.setRepositories(Arrays.asList(central, snapshots))).isSameAs(download);
        assertThat(download.getRepositories()).containsExactly(central, snapshots);
        assertThat(download.setException(failure)).isSameAs(download);
        assertThat(download.getException()).isSameAs(failure);

        download.setChecksumPolicy(null);
        download.setRepositories(null);
        assertThat(download.getChecksumPolicy()).isEmpty();
        assertThat(download.getRepositories()).isEmpty();
    }

    @Test
    void artifactUploadStoresArtifactFileExceptionAndCompletionState() {
        SimpleArtifact originalArtifact = new SimpleArtifact("org.example", "demo", "1.0.0", "jar", "sources");
        SimpleArtifact replacementArtifact = new SimpleArtifact("org.example", "demo", "1.0.1", "jar", "sources");
        File originalFile = new File(tempDirectory, "demo-1.0.0-sources.jar");
        File replacementFile = new File(tempDirectory, "demo-1.0.1-sources.jar");
        RemoteRepository releases = new RemoteRepository("releases", "default", "https://repo.example.org/releases");
        ArtifactTransferException failure = new ArtifactTransferException(replacementArtifact, releases,
                "checksum mismatch");

        ArtifactUpload upload = new ArtifactUpload(originalArtifact, originalFile);

        assertThat(upload.getArtifact()).isSameAs(originalArtifact);
        assertThat(upload.getFile()).isEqualTo(originalFile);
        assertThat(upload.getException()).isNull();
        assertThat(upload.getState()).isEqualTo(State.NEW);

        assertThat(upload.setArtifact(replacementArtifact)).isSameAs(upload);
        assertThat(upload.setFile(replacementFile)).isSameAs(upload);
        assertThat(upload.setException(failure)).isSameAs(upload);
        upload.setState(State.DONE);

        assertThat(upload.getArtifact()).isSameAs(replacementArtifact);
        assertThat(upload.getFile()).isEqualTo(replacementFile);
        assertThat(upload.getException()).isSameAs(failure);
        assertThat(upload.getState()).isEqualTo(State.DONE);
    }

    @Test
    void metadataTransfersCoverDownloadUploadPoliciesRepositoriesAndExceptions() {
        SimpleMetadata metadata = new SimpleMetadata("org.example", "demo", "1.0.0", "maven-metadata.xml",
                Metadata.Nature.RELEASE);
        File downloadFile = new File(tempDirectory, "downloaded-metadata.xml");
        File uploadFile = new File(tempDirectory, "upload-metadata.xml");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.org/maven2");
        MetadataTransferException failure = new MetadataTransferException(metadata, repository, "metadata unavailable");

        MetadataDownload download = new MetadataDownload(metadata, "resolve", downloadFile, "warn");

        assertThat(download.getMetadata()).isSameAs(metadata);
        assertThat(download.getFile()).isEqualTo(downloadFile);
        assertThat(download.getRequestContext()).isEqualTo("resolve");
        assertThat(download.getChecksumPolicy()).isEqualTo("warn");
        assertThat(download.getRepositories()).isEmpty();

        assertThat(download.setRepositories(Collections.singletonList(repository))).isSameAs(download);
        assertThat(download.setException(failure)).isSameAs(download);
        download.setState(State.ACTIVE);
        assertThat(download.getRepositories()).containsExactly(repository);
        assertThat(download.getException()).isSameAs(failure);
        assertThat(download.getState()).isEqualTo(State.ACTIVE);

        download.setRequestContext(null);
        download.setChecksumPolicy(null);
        download.setRepositories(null);
        assertThat(download.getRequestContext()).isEmpty();
        assertThat(download.getChecksumPolicy()).isEmpty();
        assertThat(download.getRepositories()).isEmpty();

        MetadataUpload upload = new MetadataUpload(metadata, uploadFile);
        assertThat(upload.getMetadata()).isSameAs(metadata);
        assertThat(upload.getFile()).isEqualTo(uploadFile);
        assertThat(upload.setException(failure)).isSameAs(upload);
        upload.setState(State.DONE);
        assertThat(upload.getException()).isSameAs(failure);
        assertThat(upload.getState()).isEqualTo(State.DONE);
    }

    @Test
    void defaultConstructedTransfersCanBePopulatedFluentlyBeforeDispatch() {
        SimpleArtifact artifact = new SimpleArtifact("org.example", "incremental", "release", "jar", "");
        SimpleMetadata metadata = new SimpleMetadata("org.example", "incremental", "metadata-release",
                "maven-metadata.xml", Metadata.Nature.RELEASE);
        File artifactFile = new File(tempDirectory, "incremental.jar");
        File metadataFile = new File(tempDirectory, "maven-metadata.xml");
        RemoteRepository repository = new RemoteRepository("central", "default", "https://repo.example.org/maven2");

        ArtifactDownload artifactDownload = new ArtifactDownload()
                .setArtifact(artifact)
                .setFile(artifactFile)
                .setRequestContext("runtime")
                .setChecksumPolicy("warn")
                .setRepositories(Collections.singletonList(repository));
        MetadataDownload metadataDownload = new MetadataDownload()
                .setMetadata(metadata)
                .setFile(metadataFile)
                .setRequestContext("metadata")
                .setChecksumPolicy("ignore")
                .setRepositories(Collections.singletonList(repository));
        MetadataUpload metadataUpload = new MetadataUpload()
                .setMetadata(metadata)
                .setFile(metadataFile);

        assertThat(artifactDownload.getState()).isEqualTo(State.NEW);
        assertThat(artifactDownload.getArtifact()).isSameAs(artifact);
        assertThat(artifactDownload.getFile()).isEqualTo(artifactFile);
        assertThat(artifactDownload.getRequestContext()).isEqualTo("runtime");
        assertThat(artifactDownload.getSupportedContexts()).containsExactly("runtime");
        assertThat(artifactDownload.getChecksumPolicy()).isEqualTo("warn");
        assertThat(artifactDownload.getRepositories()).containsExactly(repository);

        assertThat(metadataDownload.getState()).isEqualTo(State.NEW);
        assertThat(metadataDownload.getMetadata()).isSameAs(metadata);
        assertThat(metadataDownload.getFile()).isEqualTo(metadataFile);
        assertThat(metadataDownload.getRequestContext()).isEqualTo("metadata");
        assertThat(metadataDownload.getChecksumPolicy()).isEqualTo("ignore");
        assertThat(metadataDownload.getRepositories()).containsExactly(repository);

        assertThat(metadataUpload.getState()).isEqualTo(State.NEW);
        assertThat(metadataUpload.getMetadata()).isSameAs(metadata);
        assertThat(metadataUpload.getFile()).isEqualTo(metadataFile);
    }

    @Test
    void baseTransferTypesAllowPolymorphicFluentMutation() {
        SimpleArtifact artifact = new SimpleArtifact("org.example", "polymorphic", "release", "jar", "");
        File artifactFile = new File(tempDirectory, "polymorphic.jar");
        SimpleMetadata metadata = new SimpleMetadata("org.example", "polymorphic", "release", "maven-metadata.xml",
                Metadata.Nature.RELEASE);
        File metadataFile = new File(tempDirectory, "polymorphic-metadata.xml");
        ArtifactTransfer artifactDownload = new ArtifactDownload();
        ArtifactTransfer artifactUpload = new ArtifactUpload();
        MetadataTransfer metadataDownload = new MetadataDownload();
        MetadataTransfer metadataUpload = new MetadataUpload();
        List<Transfer> transfers = Arrays.asList(artifactDownload, artifactUpload, metadataDownload, metadataUpload);

        for (Transfer transfer : transfers) {
            assertThat(transfer.setState(State.ACTIVE)).isSameAs(transfer);
            assertThat(transfer.getState()).isEqualTo(State.ACTIVE);
        }

        assertThat(artifactDownload.setArtifact(artifact)).isSameAs(artifactDownload);
        assertThat(artifactDownload.setFile(artifactFile)).isSameAs(artifactDownload);
        assertThat(artifactUpload.setArtifact(artifact)).isSameAs(artifactUpload);
        assertThat(artifactUpload.setFile(artifactFile)).isSameAs(artifactUpload);
        assertThat(metadataDownload.setMetadata(metadata)).isSameAs(metadataDownload);
        assertThat(metadataDownload.setFile(metadataFile)).isSameAs(metadataDownload);
        assertThat(metadataUpload.setMetadata(metadata)).isSameAs(metadataUpload);
        assertThat(metadataUpload.setFile(metadataFile)).isSameAs(metadataUpload);

        assertThat(artifactDownload.getArtifact()).isSameAs(artifact);
        assertThat(artifactDownload.getFile()).isEqualTo(artifactFile);
        assertThat(artifactUpload.getArtifact()).isSameAs(artifact);
        assertThat(artifactUpload.getFile()).isEqualTo(artifactFile);
        assertThat(metadataDownload.getMetadata()).isSameAs(metadata);
        assertThat(metadataDownload.getFile()).isEqualTo(metadataFile);
        assertThat(metadataUpload.getMetadata()).isSameAs(metadata);
        assertThat(metadataUpload.getFile()).isEqualTo(metadataFile);
    }

    @Test
    void repositoryConnectorFactoryCreatesConnectorThatCompletesQueuedTransfers() throws Exception {
        RemoteRepository repository = new RemoteRepository("target", "default", "https://repo.example.org/releases");
        RecordingRepositoryConnectorFactory factory = new RecordingRepositoryConnectorFactory();

        RepositoryConnector connector = factory.newInstance(null, repository);
        SimpleArtifact artifact = new SimpleArtifact("org.example", "demo", "1", "jar", "");
        File downloadedArtifact = new File(tempDirectory, "demo.jar");
        ArtifactDownload artifactDownload = new ArtifactDownload(artifact, "runtime", downloadedArtifact, "ignore");
        MetadataDownload metadataDownload = new MetadataDownload(
                new SimpleMetadata("org.example", "demo", "1", "metadata", Metadata.Nature.RELEASE), "runtime",
                new File(tempDirectory, "metadata.xml"), "ignore");
        ArtifactUpload artifactUpload = new ArtifactUpload(new SimpleArtifact("org.example", "upload", "1", "jar", ""),
                new File(tempDirectory, "upload.jar"));
        MetadataUpload metadataUpload = new MetadataUpload(
                new SimpleMetadata("org.example", "upload", "1", "metadata", Metadata.Nature.RELEASE),
                new File(tempDirectory, "upload-metadata.xml"));

        connector.get(Collections.singletonList(artifactDownload), Collections.singletonList(metadataDownload));
        connector.put(Collections.singletonList(artifactUpload), Collections.singletonList(metadataUpload));
        connector.close();

        assertThat(factory.getPriority()).isEqualTo(25);
        assertThat(factory.lastRepository).isSameAs(repository);
        assertThat(factory.lastSession).isNull();
        assertThat(artifactDownload.getState()).isEqualTo(State.DONE);
        assertThat(metadataDownload.getState()).isEqualTo(State.DONE);
        assertThat(artifactUpload.getState()).isEqualTo(State.DONE);
        assertThat(metadataUpload.getState()).isEqualTo(State.DONE);
        assertThat(((RecordingRepositoryConnector) connector).closed).isTrue();
    }

    @Test
    void fileProcessorCopiesContentAndReportsProgressBuffers() throws Exception {
        FileProcessor processor = new CopyingFileProcessor();
        File source = new File(tempDirectory, "source.txt");
        File targetDirectory = new File(tempDirectory, "nested/output");
        File target = new File(targetDirectory, "target.txt");
        Files.write(source.toPath(), "aether-spi file processor".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream observedProgress = new ByteArrayOutputStream();

        assertThat(processor.mkdirs(targetDirectory)).isTrue();
        long copiedBytes = processor.copy(source, target, buffer -> {
            ByteBuffer duplicate = buffer.asReadOnlyBuffer();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            observedProgress.write(bytes);
        });

        assertThat(copiedBytes).isEqualTo(source.length());
        assertThat(Files.readAllBytes(target.toPath())).isEqualTo(Files.readAllBytes(source.toPath()));
        assertThat(observedProgress.toString(StandardCharsets.UTF_8.name())).isEqualTo("aether-spi file processor");

        processor.write(target, "replacement");
        assertThat(new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8)).isEqualTo("replacement");
    }

    @Test
    void serviceInitializationCanResolveSingleAndMultipleServicesFromLocator() {
        RecordingServiceLocator locator = new RecordingServiceLocator();
        locator.add(Logger.class, NullLogger.INSTANCE);
        locator.add(RepositoryConnectorFactory.class, new RecordingRepositoryConnectorFactory());
        locator.add(RepositoryConnectorFactory.class, new RecordingRepositoryConnectorFactory());
        InitializableService service = new InitializableService();

        service.initService(locator);

        assertThat(service.locator).isSameAs(locator);
        assertThat(service.logger).isSameAs(NullLogger.INSTANCE);
        assertThat(service.connectorFactories).hasSize(2);
        assertThat(locator.getService(String.class)).isNull();
        assertThat(locator.getServices(String.class)).isEmpty();
    }

    @Test
    void nullLoggerDisablesDebugLoggingAndAcceptsMessagesAndErrors() {
        Logger logger = NullLogger.INSTANCE;

        logger.debug("ignored");
        logger.debug("ignored with exception", new IllegalStateException("boom"));

        assertThat(logger).isInstanceOf(NullLogger.class);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(new NullLogger().isDebugEnabled()).isFalse();
    }

    private static final class SimpleArtifact implements Artifact {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String extension;
        private final String classifier;
        private final File file;
        private final Map<String, String> properties;

        private SimpleArtifact(String groupId, String artifactId, String version, String extension, String classifier) {
            this(groupId, artifactId, version, extension, classifier, null, Collections.<String, String>emptyMap());
        }

        private SimpleArtifact(String groupId, String artifactId, String version, String extension, String classifier,
                File file, Map<String, String> properties) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.extension = extension;
            this.classifier = classifier;
            this.file = file;
            this.properties = Collections.unmodifiableMap(new HashMap<String, String>(properties));
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public Artifact setVersion(String version) {
            return new SimpleArtifact(groupId, artifactId, version, extension, classifier, file, properties);
        }

        @Override
        public String getBaseVersion() {
            if (version.endsWith("-SNAPSHOT")) {
                return version.substring(0, version.length() - "-SNAPSHOT".length());
            }
            return version;
        }

        @Override
        public boolean isSnapshot() {
            return version.endsWith("-SNAPSHOT");
        }

        @Override
        public String getClassifier() {
            return classifier;
        }

        @Override
        public String getExtension() {
            return extension;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public Artifact setFile(File file) {
            return new SimpleArtifact(groupId, artifactId, version, extension, classifier, file, properties);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return properties.containsKey(key) ? properties.get(key) : defaultValue;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public Artifact setProperties(Map<String, String> properties) {
            return new SimpleArtifact(groupId, artifactId, version, extension, classifier, file,
                    properties != null ? properties : Collections.<String, String>emptyMap());
        }
    }

    private static final class SimpleMetadata implements Metadata {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String type;
        private final Nature nature;
        private final File file;

        private SimpleMetadata(String groupId, String artifactId, String version, String type, Nature nature) {
            this(groupId, artifactId, version, type, nature, null);
        }

        private SimpleMetadata(String groupId, String artifactId, String version, String type, Nature nature,
                File file) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.type = type;
            this.nature = nature;
            this.file = file;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getArtifactId() {
            return artifactId;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Nature getNature() {
            return nature;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public Metadata setFile(File file) {
            return new SimpleMetadata(groupId, artifactId, version, type, nature, file);
        }
    }

    private static final class RecordingRepositoryConnectorFactory implements RepositoryConnectorFactory {
        private RepositorySystemSession lastSession;
        private RemoteRepository lastRepository;

        @Override
        public RepositoryConnector newInstance(RepositorySystemSession session, RemoteRepository repository)
                throws NoRepositoryConnectorException {
            this.lastSession = session;
            this.lastRepository = repository;
            if (repository == null) {
                throw new NoRepositoryConnectorException(null, "repository is required");
            }
            return new RecordingRepositoryConnector();
        }

        @Override
        public int getPriority() {
            return 25;
        }
    }

    private static final class RecordingRepositoryConnector implements RepositoryConnector {
        private boolean closed;

        @Override
        public void get(Collection<? extends ArtifactDownload> artifactDownloads,
                Collection<? extends MetadataDownload> metadataDownloads) {
            for (ArtifactDownload download : artifactDownloads) {
                download.setState(State.DONE);
            }
            for (MetadataDownload download : metadataDownloads) {
                download.setState(State.DONE);
            }
        }

        @Override
        public void put(Collection<? extends ArtifactUpload> artifactUploads,
                Collection<? extends MetadataUpload> metadataUploads) {
            for (ArtifactUpload upload : artifactUploads) {
                upload.setState(State.DONE);
            }
            for (MetadataUpload upload : metadataUploads) {
                upload.setState(State.DONE);
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CopyingFileProcessor implements FileProcessor {
        @Override
        public boolean mkdirs(File directory) {
            return directory.mkdirs() || directory.isDirectory();
        }

        @Override
        public void write(File target, String data) throws IOException {
            Files.write(target.toPath(), data.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public long copy(File source, File target, ProgressListener listener) throws IOException {
            byte[] bytes = Files.readAllBytes(source.toPath());
            Files.write(target.toPath(), bytes);
            if (listener != null) {
                listener.progressed(ByteBuffer.wrap(bytes));
            }
            return bytes.length;
        }
    }

    private static final class RecordingServiceLocator implements ServiceLocator {
        private final Map<Class<?>, List<Object>> services = new HashMap<Class<?>, List<Object>>();

        private <T> void add(Class<T> type, T service) {
            List<Object> typedServices = services.get(type);
            if (typedServices == null) {
                typedServices = new ArrayList<Object>();
                services.put(type, typedServices);
            }
            typedServices.add(service);
        }

        @Override
        public <T> T getService(Class<T> type) {
            List<T> typedServices = getServices(type);
            return typedServices.isEmpty() ? null : typedServices.get(0);
        }

        @Override
        public <T> List<T> getServices(Class<T> type) {
            List<Object> typedServices = services.get(type);
            if (typedServices == null) {
                return Collections.emptyList();
            }
            List<T> result = new ArrayList<T>(typedServices.size());
            for (Object service : typedServices) {
                result.add(type.cast(service));
            }
            return result;
        }
    }

    private static final class InitializableService implements Service {
        private ServiceLocator locator;
        private Logger logger;
        private List<RepositoryConnectorFactory> connectorFactories;

        @Override
        public void initService(ServiceLocator locator) {
            this.locator = locator;
            this.logger = locator.getService(Logger.class);
            this.connectorFactories = locator.getServices(RepositoryConnectorFactory.class);
        }
    }
}
