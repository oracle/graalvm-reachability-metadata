/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.SerializedModelConfigurator;
import ch.qos.logback.classic.model.ConfigurationModel;
import ch.qos.logback.classic.model.ContextNameModel;
import ch.qos.logback.classic.model.RootLoggerModel;
import ch.qos.logback.classic.spi.Configurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class SerializedModelConfiguratorTest {

    @Test
    void appliesTheConfigurationReadFromASerializedModel(@TempDir Path directory) throws Exception {
        ConfigurationModel model = new ConfigurationModel();
        model.setTag("configuration");
        ContextNameModel name = new ContextNameModel();
        name.setTag("contextName");
        name.addText("model-context");
        model.addSubModel(name);
        RootLoggerModel root = new RootLoggerModel();
        root.setTag("root");
        root.setLevel("WARN");
        model.addSubModel(root);
        Path file = directory.resolve("logging.scmo");
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(file))) {
            output.writeObject(model);
        }

        String previous = System.getProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
        LoggerContext context = new LoggerContext();
        try {
            System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, file.toUri().toString());
            SerializedModelConfigurator configurator = new SerializedModelConfigurator();
            configurator.setContext(context);

            assertThat(configurator.configure(context)).isEqualTo(Configurator.ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY);
            assertThat(context.getName()).isEqualTo("model-context");
            assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.WARN);
            assertThat(context.getLogger("application").isInfoEnabled()).isFalse();
            assertThat(context.getLogger("application").isWarnEnabled()).isTrue();
        } finally {
            if (previous == null) {
                System.clearProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY);
            } else {
                System.setProperty(ClassicConstants.MODEL_CONFIG_FILE_PROPERTY, previous);
            }
            context.stop();
        }
    }
}
