/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_awspring_cloud.spring_cloud_aws_autoconfigure;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.awspring.cloud.autoconfigure.config.BootstrapLoggingHelper;
import io.awspring.cloud.autoconfigure.config.s3.S3PropertySources;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLogFactory;

public class BootstrapLoggingHelperTest {
    private static final Log DEFERRED_LOG = LogFactory.getLog("bootstrap-deferred-log");

    @Test
    void reconfigureLoggersUpdatesAwsConfigPropertySourceLogger() {
        FixedLogFactory logFactory = new FixedLogFactory(DEFERRED_LOG);
        String propertySourcesClass = S3PropertySources.class.getName();

        assertThatCode(() -> BootstrapLoggingHelper.reconfigureLoggers(logFactory,
                propertySourcesClass)).doesNotThrowAnyException();
    }

    private static final class FixedLogFactory implements DeferredLogFactory {
        private final Log log;

        private FixedLogFactory(Log log) {
            this.log = log;
        }

        @Override
        public Log getLog(Supplier<Log> destination) {
            return log;
        }
    }
}
