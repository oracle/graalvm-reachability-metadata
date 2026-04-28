/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.SerializedModelConfigurator;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializedModelConfiguratorTest {

  private final LoggerContext loggerContext = new LoggerContext();

  @TempDir
  private Path tempDir;

  @Test
  void readsSerializedModelFileSelectedBySystemProperty() throws Exception {
    Path modelFile = tempDir.resolve("logback-test" + CoreConstants.MODEL_CONFIG_FILE_EXTENSION);
    writeSerializedNullModel(modelFile);

    System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, modelFile.toString());

    SerializedModelConfigurator configurator = new SerializedModelConfigurator();
    configurator.setContext(loggerContext);
    Configurator.ExecutionStatus status = configurator.configure(loggerContext);

    assertThat(status).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
    assertThat(loggerContext.getStatusManager().getCopyOfStatusList())
            .extracting(Status::getMessage)
            .contains("Empty model. Abandoning.");
  }

  @AfterEach
  void tearDown() {
    System.clearProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
    loggerContext.stop();
  }

  private void writeSerializedNullModel(Path modelFile) throws Exception {
    Files.createDirectories(modelFile.getParent());
    try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(modelFile))) {
      outputStream.writeObject(null);
    }
  }
}
