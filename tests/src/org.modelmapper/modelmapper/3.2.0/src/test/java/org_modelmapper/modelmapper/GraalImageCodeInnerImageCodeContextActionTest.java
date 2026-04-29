/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.PrivilegedAction;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.utility.GraalImageCode;

public class GraalImageCodeInnerImageCodeContextActionTest {
    private static final String ACTION_TYPE_NAME =
        "org.modelmapper.internal.bytebuddy.utility.GraalImageCode$ImageCodeContextAction";

    @Test
    void queriesRuntimeArgumentsForGraalImageAgentDetection() throws Exception {
        GraalImageCode result = imageCodeContextAction().run();

        assertThat(result).isIn(GraalImageCode.AGENT, GraalImageCode.NONE);
    }

    @SuppressWarnings("unchecked")
    private static PrivilegedAction<GraalImageCode> imageCodeContextAction() throws ClassNotFoundException {
        Class<?> actionType = Class.forName(ACTION_TYPE_NAME, true, GraalImageCode.class.getClassLoader());
        return Arrays.stream(actionType.getEnumConstants())
            .filter(action -> "INSTANCE".equals(((Enum<?>) action).name()))
            .map(action -> (PrivilegedAction<GraalImageCode>) action)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Image code context action is unavailable"));
    }
}
