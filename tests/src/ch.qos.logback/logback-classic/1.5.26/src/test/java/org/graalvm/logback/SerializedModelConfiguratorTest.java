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
import ch.qos.logback.core.model.Model;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializedModelConfiguratorTest {

  @Test
  void shouldConfigureLoggerContextFromSerializedModelFile() throws Exception {
    Path serializedModelFile = Files.createTempFile("logback-serialized-model", ".scmo");
    String previousModelFile = System.getProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
    LoggerContext context = new LoggerContext();
    context.setName("serialized-model-configurator-test");

    try {
      Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
      rootLogger.setLevel(Level.DEBUG);
      writeModel(serializedModelFile, createRootLevelModel(Level.INFO));
      System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, serializedModelFile.toString());

      SerializedModelConfigurator configurator = new SerializedModelConfigurator();
      configurator.setContext(context);
      Configurator.ExecutionStatus status = configurator.configure(context);

      assertThat(status).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
      assertThat(rootLogger.getLevel()).isEqualTo(Level.INFO);
      assertThat(context.getStatusManager().getCopyOfStatusList())
          .anyMatch(entry -> entry.getMessage().contains("Model at [")
              && entry.getMessage().contains("read in"));
    } finally {
      restoreModelFileProperty(previousModelFile);
      context.stop();
      Files.deleteIfExists(serializedModelFile);
    }
  }

  private Model createRootLevelModel(Level rootLevel) {
    ConfigurationModel configurationModel = new ConfigurationModel();
    configurationModel.setTag("configuration");
    configurationModel.setDebugStr("false");
    configurationModel.setPackagingDataStr("false");

    RootLoggerModel rootLoggerModel = new RootLoggerModel();
    rootLoggerModel.setTag("root");
    rootLoggerModel.setLevel(rootLevel.toString());
    configurationModel.addSubModel(rootLoggerModel);
    return configurationModel;
  }

  private void writeModel(Path serializedModelFile, Model model) throws Exception {
    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(
        Files.newOutputStream(serializedModelFile))) {
      objectOutputStream.writeObject(model);
    }
  }

  private void restoreModelFileProperty(String previousModelFile) {
    if (previousModelFile == null) {
      System.clearProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
    } else {
      System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, previousModelFile);
    }
  }
}
