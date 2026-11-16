/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.bridge.IMessage;
import org.aspectj.weaver.Lint;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LintTest {
    @Test
    void restoresDefaultDiagnosticSeverityFromBundledProperties() {
        ReflectionWorld world = new ReflectionWorld(getClass().getClassLoader());
        Lint lint = world.getLint();
        lint.adviceDidNotMatch.setKind(IMessage.ERROR);
        lint.loadDefaultProperties();

        assertThat(lint.adviceDidNotMatch.getKind()).isEqualTo(IMessage.WARNING);
    }
}
