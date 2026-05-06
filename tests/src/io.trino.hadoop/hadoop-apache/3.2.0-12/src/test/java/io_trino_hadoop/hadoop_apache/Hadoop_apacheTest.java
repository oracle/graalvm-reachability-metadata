/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import io.trino.hadoop.SocksSocketFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.StorageUnit;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.fs.permission.AclEntry;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.alias.CredentialProvider;
import org.apache.hadoop.security.alias.CredentialProviderFactory;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.LineReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class Hadoop_apacheTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void configurationLoadsDefaultsWritesXmlAndCreatesConfiguredInstances() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setBoolean("example.boolean", true);
        configuration.setInt("example.int", 42);
        configuration.setStrings("example.list", "alpha", "beta", "gamma");
        configuration.setTimeDuration("example.timeout", 1500, TimeUnit.MILLISECONDS);
        configuration.setStorageSize("example.storage", 2, StorageUnit.MB);
        configuration.setClass("example.socket.factory", SocksSocketFactory.class, SocksSocketFactory.class);

        assertThat(configuration.get("fs.defaultFS")).isNotBlank();
        assertThat(configuration.getBoolean("example.boolean", false)).isTrue();
        assertThat(configuration.getInt("example.int", 0)).isEqualTo(42);
        assertThat(configuration.getTrimmedStrings("example.list")).containsExactly("alpha", "beta", "gamma");
        assertThat(configuration.getTimeDuration("example.timeout", 0, TimeUnit.MILLISECONDS)).isEqualTo(1500);
        assertThat(configuration.getStorageSize("example.storage", 0, StorageUnit.BYTES)).isEqualTo(2 * 1024 * 1024);

        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        configuration.writeXml(xml);
        assertThat(xml.toString(StandardCharsets.UTF_8)).contains(
                "<name>example.boolean</name>",
                "<value>true</value>",
                "<name>example.list</name>",
                "<value>alpha,beta,gamma</value>");

        List<SocksSocketFactory> factories = configuration.getInstances(
                "example.socket.factory",
                SocksSocketFactory.class);
        assertThat(factories).hasSize(1);
        assertThat(factories.get(0).getConf()).isSameAs(configuration);
    }

    @Test
    void localFileSystemCreatesReadsListsRenamesAndDeletesFiles() throws Exception {
        Configuration configuration = new Configuration(false);
        configuration.setBoolean("fs.file.impl.disable.cache", true);

        Path root = new Path(temporaryDirectory.toURI());
        Path file = new Path(root, "sample.txt");
        Path renamed = new Path(root, "renamed.txt");
        byte[] content = "first line\nsecond line".getBytes(StandardCharsets.UTF_8);

        try (FileSystem fileSystem = new RawLocalFileSystem()) {
            fileSystem.initialize(URI.create("file:///"), configuration);
            assertThat(fileSystem.getUri()).isEqualTo(URI.create("file:///"));
            fileSystem.mkdirs(root);
            assertThat(fileSystem.exists(root)).isTrue();

            try (FSDataOutputStream outputStream = fileSystem.create(file, true)) {
                outputStream.write(content);
            }

            FileStatus status = fileSystem.getFileStatus(file);
            assertThat(status.isFile()).isTrue();
            assertThat(status.getLen()).isEqualTo(content.length);
            assertThat(status.getPath().getName()).isEqualTo("sample.txt");
            assertThat(fileSystem.listStatus(root, path -> path.getName().equals("sample.txt")))
                    .extracting(FileStatus::getPath)
                    .extracting(Path::getName)
                    .containsExactly("sample.txt");

            byte[] readBack = new byte[content.length];
            try (FSDataInputStream inputStream = fileSystem.open(file)) {
                inputStream.readFully(0, readBack);
                inputStream.seek("first ".length());
                assertThat(inputStream.getPos()).isEqualTo("first ".length());
            }
            assertThat(readBack).isEqualTo(content);

            assertThat(fileSystem.rename(file, renamed)).isTrue();
            assertThat(fileSystem.exists(file)).isFalse();
            assertThat(fileSystem.exists(renamed)).isTrue();
            assertThat(fileSystem.delete(renamed, false)).isTrue();
            assertThat(fileSystem.exists(renamed)).isFalse();
        }
    }

    @Test
    void compressionCodecFactoryDiscoversAndRoundTripsCodecs() throws Exception {
        CompressionCodecFactory factory = new CompressionCodecFactory(new Configuration());

        CompressionCodec gzip = factory.getCodec(new Path("part-00000.txt.gz"));
        CompressionCodec bzip2 = factory.getCodec(new Path("part-00000.txt.bz2"));

        assertThat(gzip).isInstanceOf(GzipCodec.class);
        assertThat(bzip2).isInstanceOf(BZip2Codec.class);
        assertThat(factory.getCodecByClassName(GzipCodec.class.getName())).isInstanceOf(GzipCodec.class);
        assertThat(factory.getCodecByName(BZip2Codec.class.getName())).isInstanceOf(BZip2Codec.class);
        assertThat(factory.getCodecClassByName(GzipCodec.class.getName())).isEqualTo(GzipCodec.class);
        assertThat(factory.getCodec(new Path("part-00000.txt"))).isNull();

        assertRoundTrips(gzip, "gzip payload with unicode π and multiple words");
        assertRoundTrips(bzip2, "bzip2 payload\nwith more than one line\n");
    }

    @Test
    void sequenceFileStoresAndReadsWritableKeyValueRecords() throws Exception {
        Configuration configuration = new Configuration();
        Path records = new Path(new File(temporaryDirectory, "records.seq").toURI());

        try (FileSystem fileSystem = new RawLocalFileSystem()) {
            fileSystem.initialize(URI.create("file:///"), configuration);
            try (FSDataOutputStream outputStream = fileSystem.create(records, true);
                    SequenceFile.Writer writer = SequenceFile.createWriter(
                            configuration,
                            SequenceFile.Writer.stream(outputStream),
                            SequenceFile.Writer.keyClass(Text.class),
                            SequenceFile.Writer.valueClass(IntWritable.class),
                            SequenceFile.Writer.compression(SequenceFile.CompressionType.NONE))) {
                writer.append(new Text("alpha"), new IntWritable(1));
                writer.append(new Text("beta"), new IntWritable(2));
                writer.append(new Text("gamma"), new IntWritable(3));
                assertThat(writer.getLength()).isPositive();
            }

            try (FSDataInputStream inputStream = fileSystem.open(records);
                    SequenceFile.Reader reader = new SequenceFile.Reader(
                            configuration,
                            SequenceFile.Reader.stream(inputStream),
                            SequenceFile.Reader.length(fileSystem.getFileStatus(records).getLen()))) {
                assertThat(reader.getKeyClass()).isEqualTo(Text.class);
                assertThat(reader.getValueClass()).isEqualTo(IntWritable.class);
                assertThat(reader.isCompressed()).isFalse();
                assertThat(reader.isBlockCompressed()).isFalse();

                Text key = new Text();
                IntWritable value = new IntWritable();
                assertThat(reader.next(key, value)).isTrue();
                assertThat(key.toString()).isEqualTo("alpha");
                assertThat(value.get()).isEqualTo(1);

                assertThat(reader.next(key, value)).isTrue();
                assertThat(key.toString()).isEqualTo("beta");
                assertThat(value.get()).isEqualTo(2);

                assertThat(reader.next(key, value)).isTrue();
                assertThat(key.toString()).isEqualTo("gamma");
                assertThat(value.get()).isEqualTo(3);
                assertThat(reader.next(key, value)).isFalse();
            }
        }
    }

    @Test
    void mapFileIndexesSortedWritableRecordsAndSupportsKeyLookups() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setBoolean("fs.file.impl.disable.cache", true);
        Path mapDirectory = new Path(new File(temporaryDirectory, "indexed-records.map").toURI());

        try (MapFile.Writer writer = new MapFile.Writer(
                configuration,
                mapDirectory,
                MapFile.Writer.keyClass(Text.class),
                MapFile.Writer.valueClass(IntWritable.class))) {
            writer.setIndexInterval(1);
            writer.append(new Text("alpha"), new IntWritable(10));
            writer.append(new Text("beta"), new IntWritable(20));
            writer.append(new Text("delta"), new IntWritable(40));
            writer.append(new Text("gamma"), new IntWritable(30));
        }

        try (MapFile.Reader reader = new MapFile.Reader(mapDirectory, configuration)) {
            assertThat(reader.getKeyClass()).isEqualTo(Text.class);
            assertThat(reader.getValueClass()).isEqualTo(IntWritable.class);

            IntWritable value = new IntWritable();
            assertThat(reader.get(new Text("beta"), value)).isSameAs(value);
            assertThat(value.get()).isEqualTo(20);

            value.set(-1);
            assertThat(reader.get(new Text("missing"), value)).isNull();
            assertThat(value.get()).isEqualTo(-1);

            Text nextKey = (Text) reader.getClosest(new Text("blueberry"), value);
            assertThat(nextKey.toString()).isEqualTo("delta");
            assertThat(value.get()).isEqualTo(40);

            Text previousKey = (Text) reader.getClosest(new Text("blueberry"), value, true);
            assertThat(previousKey.toString()).isEqualTo("beta");
            assertThat(value.get()).isEqualTo(20);

            Text finalKey = new Text();
            reader.finalKey(finalKey);
            assertThat(finalKey.toString()).isEqualTo("gamma");

            assertThat(reader.seek(new Text("delta"))).isTrue();
            assertThat(reader.seek(new Text("blueberry"))).isFalse();
        }
    }

    @Test
    void permissionsAclsTextAndLineReaderHandleHadoopValueTypes() throws Exception {
        FsPermission permission = new FsPermission(FsAction.ALL, FsAction.READ_EXECUTE, FsAction.NONE);
        assertThat(permission.toShort()).isEqualTo((short) 0750);
        assertThat(permission.applyUMask(new FsPermission((short) 0027)).toString()).isEqualTo("rwxr-x---");
        assertThat(FsPermission.valueOf("-rw-r-----").getGroupAction()).isEqualTo(FsAction.READ);

        List<AclEntry> aclEntries = AclEntry.parseAclSpec("user::rwx,user:analytics:r--,group::r-x,other::---", true);
        assertThat(aclEntries).hasSize(4);
        assertThat(AclEntry.aclSpecToString(aclEntries)).contains("user:analytics:r--");

        Text text = new Text("hello");
        byte[] suffix = " π".getBytes(StandardCharsets.UTF_8);
        text.append(suffix, 0, suffix.length);
        assertThat(text.toString()).isEqualTo("hello π");
        assertThat(text.find("π")).isGreaterThan(0);

        ByteBuffer encoded = Text.encode("encoded ✓");
        byte[] encodedBytes = new byte[encoded.remaining()];
        encoded.get(encodedBytes);
        assertThat(Text.decode(encodedBytes)).isEqualTo("encoded ✓");

        byte[] lines = "alpha\nbeta\r\ngamma".getBytes(StandardCharsets.UTF_8);
        try (LineReader reader = new LineReader(new ByteArrayInputStream(lines))) {
            Text line = new Text();
            assertThat(reader.readLine(line)).isEqualTo("alpha\n".getBytes(StandardCharsets.UTF_8).length);
            assertThat(line.toString()).isEqualTo("alpha");
            assertThat(reader.readLine(line)).isEqualTo("beta\r\n".getBytes(StandardCharsets.UTF_8).length);
            assertThat(line.toString()).isEqualTo("beta");
            assertThat(reader.readLine(line)).isEqualTo("gamma".getBytes(StandardCharsets.UTF_8).length);
            assertThat(line.toString()).isEqualTo("gamma");
            assertThat(reader.readLine(line)).isZero();
        }
    }

    @Test
    void userGroupInformationCredentialsAndCredentialProviderWorkTogether() throws Exception {
        UserGroupInformation.setConfiguration(new Configuration(false));
        UserGroupInformation realUser = UserGroupInformation.createRemoteUser("alice@EXAMPLE.COM");
        UserGroupInformation proxyUser = UserGroupInformation.createProxyUser("analytics", realUser);

        Token<TokenIdentifier> token = new Token<>(
                new byte[] {1, 2, 3},
                new byte[] {4, 5, 6},
                new Text("test-kind"),
                new Text("test-service"));
        Text tokenAlias = new Text("integration-token");

        assertThat(proxyUser.getShortUserName()).isEqualTo("analytics");
        assertThat(proxyUser.getRealUser()).isEqualTo(realUser);
        assertThat(proxyUser.addToken(tokenAlias, token)).isTrue();
        assertThat(proxyUser.getTokens()).contains(token);

        Credentials credentials = proxyUser.getCredentials();
        assertThat(credentials.getToken(tokenAlias)).isEqualTo(token);
        assertThat(credentials.numberOfTokens()).isEqualTo(1);

        Configuration providerConfiguration = new Configuration(false);
        File keyStoreFile = new File(temporaryDirectory, "credentials.jceks");
        String keyStorePath = keyStoreFile.getAbsolutePath().replace(File.separatorChar, '/');
        providerConfiguration.set(
                CredentialProviderFactory.CREDENTIAL_PROVIDER_PATH,
                "localjceks://file" + keyStorePath);
        List<CredentialProvider> providers = CredentialProviderFactory.getProviders(providerConfiguration);
        assertThat(providers).hasSize(1);

        CredentialProvider provider = providers.get(0);
        String alias = "integration.password." + System.nanoTime();
        char[] password = "changeit".toCharArray();
        CredentialProvider.CredentialEntry created = provider.createCredentialEntry(alias, password);
        provider.flush();

        assertThat(created.getAlias()).isEqualTo(alias);
        assertThat(provider.getAliases()).contains(alias);
        assertThat(provider.getCredentialEntry(alias).getCredential()).containsExactly(password);

        provider.deleteCredentialEntry(alias);
        provider.flush();
        assertThat(provider.getCredentialEntry(alias)).isNull();
    }

    @Test
    void socksSocketFactoryCreatesUnconnectedSocketsFromConfiguration() throws Exception {
        Configuration configuration = new Configuration(false);
        configuration.set("hadoop.socks.server", "127.0.0.1:1");

        SocksSocketFactory socketFactory = new SocksSocketFactory();
        socketFactory.setConf(configuration);

        try (Socket socket = socketFactory.createSocket()) {
            assertThat(socket.isConnected()).isFalse();
            assertThat(socket.isClosed()).isFalse();
            assertThat(socketFactory.getConf()).isSameAs(configuration);
        }
    }

    private static void assertRoundTrips(CompressionCodec codec, String payload) throws IOException {
        byte[] expected = payload.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (CompressionOutputStream outputStream = codec.createOutputStream(compressed)) {
            outputStream.write(expected);
        }

        byte[] actual;
        ByteArrayInputStream inputBytes = new ByteArrayInputStream(compressed.toByteArray());
        try (CompressionInputStream inputStream = codec.createInputStream(inputBytes)) {
            actual = inputStream.readAllBytes();
        }
        assertThat(actual).isEqualTo(expected);
    }
}
