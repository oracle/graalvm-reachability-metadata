/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.basic.logex.ExceptionWrapper;
import org.glassfish.pfl.basic.logex.Log;
import org.glassfish.pfl.basic.logex.LogLevel;
import org.glassfish.pfl.basic.logex.WrapperGenerator;
import org.junit.jupiter.api.Test;

public class WrapperGeneratorAnonymous4Test {
    @ExceptionWrapper(idPrefix = "X")
    public interface Factory {
        @Log(level = LogLevel.WARNING, id = 2)
        IllegalStateException state(String message);
    }

    @Test
    public void generatedProxyImplementsWrapperInterface() {
        Factory factory = WrapperGenerator.makeWrapper(Factory.class);

        assertThat(factory.state("state")).hasMessage("state");
    }
}
