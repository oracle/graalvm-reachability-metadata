/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.eclipse.jdt.internal.compiler.util.Messages;
import org.junit.jupiter.api.Test;

public class MessagesTest {
    private static final String COMPILER_MESSAGES_BUNDLE = "org.eclipse.jdt.internal.compiler.messages";
    private static final String MISSING_MESSAGES_BUNDLE = "org_eclipse_jdt.ecj.missing_messages";

    @Test
    void initializeMessagesAssignsFallbackTextToMissingPublicMessageFields() {
        String originalCompilationDoneMessage = Messages.compilation_done;

        try {
            Messages.compilation_done = null;

            Messages.initializeMessages(MISSING_MESSAGES_BUNDLE, Messages.class);

            assertThat(Messages.compilation_done)
                    .isEqualTo("Missing message: compilation_done in: " + MISSING_MESSAGES_BUNDLE);
        } finally {
            Messages.compilation_done = originalCompilationDoneMessage;
        }
    }

    @Test
    void initializeMessagesReadsCompilerMessagesThroughSystemResourceLookupForBootstrapClasses() {
        assertThatCode(() -> Messages.initializeMessages(COMPILER_MESSAGES_BUNDLE, int.class))
                .doesNotThrowAnyException();
    }
}
