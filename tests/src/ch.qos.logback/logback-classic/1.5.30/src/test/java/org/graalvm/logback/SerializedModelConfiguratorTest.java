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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.SerializedModelConfigurator;
import ch.qos.logback.classic.model.ConfigurationModel;
import ch.qos.logback.classic.model.RootLoggerModel;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static ch.qos.logback.core.CoreConstants.MODEL_CONFIG_FILE_EXTENSION;
import static org.assertj.core.api.Assertions.assertThat;

public class SerializedModelConfiguratorTest {

  private String previousModelConfigFile;

  @BeforeEach
  void rememberModelConfigFileProperty() {
    previousModelConfigFile = System.getProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
  }

  @AfterEach
  void restoreModelConfigFileProperty() {
    if (previousModelConfigFile == null) {
      System.clearProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
    } else {
      System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, previousModelConfigFile);
    }
  }

  @Test
  void configureReadsSerializedModelFromSystemProperty() throws Exception {
    Path modelFile = Files.createTempFile("serialized-logback-model", MODEL_CONFIG_FILE_EXTENSION);
    writeSerializedModel(modelFile, createConfigurationModel(Level.INFO));
    System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, modelFile.toString());

    LoggerContext loggerContext = new LoggerContext();
    SerializedModelConfigurator configurator = new SerializedModelConfigurator();
    configurator.setContext(loggerContext);

    try {
      Configurator.ExecutionStatus executionStatus = configurator.configure(loggerContext);

      assertThat(executionStatus).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
      assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.INFO);
      assertThat(loggerContext.getStatusManager().getCopyOfStatusList())
          .extracting(Status::getMessage)
          .anySatisfy(message -> assertThat(message).contains("read in"));
    } finally {
      loggerContext.stop();
      Files.deleteIfExists(modelFile);
    }
  }

  private static ConfigurationModel createConfigurationModel(Level rootLevel) {
    ConfigurationModel configurationModel = new ConfigurationModel();
    configurationModel.setScanStr("false");

    RootLoggerModel rootLoggerModel = new RootLoggerModel();
    rootLoggerModel.setLevel(rootLevel.toString());
    configurationModel.addSubModel(rootLoggerModel);

    return configurationModel;
  }

  private static void writeSerializedModel(Path path, ConfigurationModel model) throws Exception {
    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(Files.newOutputStream(path))) {
      objectOutputStream.writeObject(model);
    }
  }
}
