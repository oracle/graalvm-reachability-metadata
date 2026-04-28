/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.antadapter.AntAdapterMessages;
import org.junit.jupiter.api.Test;

public class AntAdapterMessagesTest {
    @Test
    void readsAntAdapterMessageBundleOnClassInitialization() {
        assertThat(AntAdapterMessages.getString("ant.jdtadapter.info.usingJDTCompiler"))
                .isEqualTo("Using JDT compiler");
    }

    @Test
    void formatsAntAdapterMessagesWithArguments() {
        assertThat(AntAdapterMessages.getString("ant.jdtadapter.error.compilationFailed", "build.log"))
                .isEqualTo("Compilation failed. Compiler errors are available in build.log");
    }

    @Test
    void returnsBangWrappedKeyForMissingMessages() {
        assertThat(AntAdapterMessages.getString("missing.ant.adapter.message"))
                .isEqualTo("!missing.ant.adapter.message!");
    }
}
