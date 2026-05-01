/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt_core_compiler.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.antadapter.AntAdapterMessages;
import org.junit.jupiter.api.Test;

public class AntAdapterMessagesTest {
    @Test
    void loadsAntAdapterMessagesResourceBundle() {
        String message = AntAdapterMessages.getString("ant.jdtadapter.info.usingJDTCompiler");

        assertThat(message).isEqualTo("Using JDT compiler");
    }

    @Test
    void formatsAntAdapterMessageWithArgument() {
        String message = AntAdapterMessages.getString("buildJarIndex.ioexception.occured", "disk full");

        assertThat(message).isEqualTo("IOException - disk full");
    }

    @Test
    void returnsDelimitedKeyForMissingAntAdapterMessage() {
        String message = AntAdapterMessages.getString("missing.ant.adapter.message");

        assertThat(message).isEqualTo("!missing.ant.adapter.message!");
    }
}
