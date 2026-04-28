/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import org.junit.jupiter.api.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;

public class AgentTest {

    @Test
    void delegatesPremainToTheShadowLoadedAgentLauncher() throws Exception {
        Class<?> agentClass = Class.forName("lombok.launch.Agent");
        Method premain = agentClass.getDeclaredMethod("premain", String.class, Instrumentation.class);
        premain.setAccessible(true);

        assertThatCode(() -> premain.invoke(null, "", null)).doesNotThrowAnyException();
    }
}
