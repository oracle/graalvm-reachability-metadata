/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.logback;

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.SerializedModelConfigurator;
import ch.qos.logback.classic.model.ConfigurationModel;
import ch.qos.logback.classic.model.RootLoggerModel;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializedModelConfiguratorTest {

  @Test
  void configureReadsSerializedModelFromConfiguredLocation(@TempDir Path tempDir) throws Exception {
    Path serializedModel = tempDir.resolve("logback-test.scmo");
    writeSerializedModel(serializedModel);

    LoggerContext loggerContext = new LoggerContext();
    SerializedModelConfigurator configurator = new SerializedModelConfigurator();
    configurator.setContext(loggerContext);
    String previousModelFile = System.getProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
    try {
      System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, serializedModel.toString());

      Configurator.ExecutionStatus executionStatus = configurator.configure(loggerContext);

      assertThat(executionStatus).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
      assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.ERROR);
      List<Status> statuses = loggerContext.getStatusManager().getCopyOfStatusList();
      assertThat(statuses)
          .extracting(Status::getMessage)
          .anyMatch(message -> message.startsWith("Model at [") && message.contains("logback-test.scmo"));
    } finally {
      restoreSystemProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, previousModelFile);
      loggerContext.stop();
    }
  }

  private static void writeSerializedModel(Path serializedModel) throws Exception {
    ConfigurationModel configurationModel = new ConfigurationModel();
    configurationModel.setTag("configuration");
    RootLoggerModel rootLoggerModel = new RootLoggerModel();
    rootLoggerModel.setTag("root");
    rootLoggerModel.setLevel(Level.ERROR.toString());
    configurationModel.addSubModel(rootLoggerModel);

    try (OutputStream fileOutputStream = Files.newOutputStream(serializedModel);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
      objectOutputStream.writeObject(configurationModel);
    }
  }

  private static void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
