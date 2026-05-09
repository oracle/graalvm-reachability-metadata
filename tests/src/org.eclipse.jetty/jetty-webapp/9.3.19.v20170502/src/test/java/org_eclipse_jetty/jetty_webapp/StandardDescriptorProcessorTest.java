/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jetty.webapp.StandardDescriptorProcessor;
import org.junit.jupiter.api.Test;

public class StandardDescriptorProcessorTest {
    @Test
    void createsProcessorWithStandardDescriptorVisitors() {
        StandardDescriptorProcessor processor = new StandardDescriptorProcessor();

        assertThat(processor).isNotNull();
    }
}
