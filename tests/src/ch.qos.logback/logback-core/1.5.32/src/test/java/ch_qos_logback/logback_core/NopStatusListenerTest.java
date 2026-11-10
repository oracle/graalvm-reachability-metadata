/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.NopStatusListener;
import ch.qos.logback.core.status.Status;
import ch_qos_logback.logback_core.ConsoleAppenderTest.AttachableContext;
import ch_qos_logback.logback_core.ConsoleAppenderTest.CoreAppenderConfigurator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class NopStatusListenerTest {
    @Test
    void xmlInstallsNoOpListenerThatAcceptsContextStatusEvents() throws Exception {
        AttachableContext context = new AttachableContext();
        CoreAppenderConfigurator configurator = new CoreAppenderConfigurator();
        configurator.setContext(context);

        try {
            configurator.doConfigure(new ByteArrayInputStream("""
                    <configuration>
                        <statusListener class="ch.qos.logback.core.status.NopStatusListener"/>
                    </configuration>
                    """.getBytes(StandardCharsets.UTF_8)));

            assertThat(context.getStatusManager().getCopyOfStatusListenerList())
                    .singleElement().isInstanceOf(NopStatusListener.class);
            InfoStatus status = new InfoStatus("application configured", context);
            context.getStatusManager().add(status);

            assertThat(context.getStatusManager().getCopyOfStatusList()).contains(status);
            assertThat(context.getStatusManager().getCopyOfStatusList())
                    .extracting(Status::getLevel).containsOnly(Status.INFO);
        } finally {
            context.stop();
        }
    }
}
