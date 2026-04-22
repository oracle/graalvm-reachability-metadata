/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import java.lang.instrument.Instrumentation;

import org.junit.jupiter.api.Test;

public class AgentDynamicAccessTest {
    @Test
    void agentPremainDelegatesToTheShadowLauncher() throws Throwable {
        LombokLaunchTestSupport.invokeStatic(
                "lombok.launch.Agent",
                "premain",
                new Class<?>[] {String.class, Instrumentation.class},
                "",
                null);
    }
}
