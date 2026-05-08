/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesConfigBase;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

public class PipesClientTest {

    @TempDir
    private Path tempDir;

    @Test
    public void processSerializesTupleAndDeserializesIntermediateAndFinalResults()
            throws Exception {
        Path inputFile = tempDir.resolve("message.txt");
        Files.writeString(inputFile, "hello from pipes", StandardCharsets.UTF_8);
        Path tikaConfig = writePipesConfig();

        PipesConfigBase config = new PipesConfigBase();
        config.setTikaConfig(tikaConfig);
        config.setJavaPath(javaExecutable().toString());
        config.setForkedJvmArgs(List.of("-Xmx64m", "-Djava.awt.headless=true"));
        config.setMaxForEmitBatchBytes(-1L);
        config.setStartupTimeoutMillis(10_000L);
        config.setSleepOnStartupTimeoutMillis(100L);
        config.setTimeoutMillis(10_000L);
        config.setShutdownClientAfterMillis(5_000L);
        config.setMaxFilesProcessedPerProcess(0);

        FetchEmitTuple tuple = new FetchEmitTuple(
                "task-1",
                new FetchKey("fs", inputFile.toString()),
                new EmitKey("unused", "message-result"));

        try (PipesClient client = new PipesClient(config)) {
            PipesResult result = client.process(tuple);

            assertThat(result.getStatus()).isEqualTo(PipesResult.STATUS.PARSE_SUCCESS);
            assertThat(result.getEmitData()).isNotNull();
            assertThat(result.getEmitData().getEmitKey())
                    .isEqualTo(new EmitKey("unused", "message-result"));
            assertThat(result.getEmitData().getMetadataList()).hasSize(1);
            Metadata metadata = result.getEmitData().getMetadataList().get(0);
            assertThat(metadata.get(TikaCoreProperties.SOURCE_PATH))
                    .isEqualTo(inputFile.toString());
            assertThat(metadata.get(Metadata.CONTENT_TYPE)).isNotBlank();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Path writePipesConfig() throws Exception {
        Path tikaConfig = tempDir.resolve("tika-pipes.xml");
        String xml = """
                <properties>
                  <fetchers>
                    <fetcher class="org.apache.tika.pipes.fetcher.fs.FileSystemFetcher">
                      <name>fs</name>
                      <allowAbsolutePaths>true</allowAbsolutePaths>
                    </fetcher>
                  </fetchers>
                </properties>
                """;
        Files.writeString(tikaConfig, xml, StandardCharsets.UTF_8);
        return tikaConfig;
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
