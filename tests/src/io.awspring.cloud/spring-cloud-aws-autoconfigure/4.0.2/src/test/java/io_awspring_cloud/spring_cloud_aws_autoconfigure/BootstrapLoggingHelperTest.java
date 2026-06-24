/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_awspring_cloud.spring_cloud_aws_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.awspring.cloud.autoconfigure.config.parameterstore.ParameterStoreConfigDataLoader;
import io.awspring.cloud.autoconfigure.config.parameterstore.ParameterStorePropertySources;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLogFactory;

public class BootstrapLoggingHelperTest {

    @Test
    void parameterStoreLoaderReconfiguresBootstrapLoggers() {
        RecordingDeferredLogFactory logFactory = new RecordingDeferredLogFactory();

        ParameterStoreConfigDataLoader loader = new ParameterStoreConfigDataLoader(logFactory);

        assertThat(loader).isNotNull();
        assertThat(logFactory.requestedLogClasses()).contains(ParameterStorePropertySources.class);
    }

    private static final class RecordingDeferredLogFactory implements DeferredLogFactory {
        private final List<Class<?>> requestedLogClasses = new ArrayList<>();

        @Override
        public Log getLog(Class<?> type) {
            requestedLogClasses.add(type);
            return LogFactory.getLog(type);
        }

        @Override
        public Log getLog(Supplier<Log> destination) {
            return destination.get();
        }

        private List<Class<?>> requestedLogClasses() {
            return requestedLogClasses;
        }
    }
}
