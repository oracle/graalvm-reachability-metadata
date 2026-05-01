/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;

import org.eclipse.jdt.internal.compiler.util.Messages;
import org.junit.jupiter.api.Test;

public class MessagesTest {
    private static final String MISSING_BUNDLE_NAME = "org.example.missing.messages";

    public static String sampleMessage;

    @Test
    void initializeMessagesSetsMissingPublicStaticMessages() {
        sampleMessage = null;

        Messages.initializeMessages(MISSING_BUNDLE_NAME, MessagesTest.class);

        assertThat(sampleMessage).isEqualTo("Missing message: sampleMessage in: " + MISSING_BUNDLE_NAME);
    }

    @Test
    void loadUsesSystemResourceLookupWhenClassLoaderIsNull() {
        final Field[] fields = new Field[0];

        assertThatCode(() -> Messages.load(MISSING_BUNDLE_NAME, null, fields)).doesNotThrowAnyException();
    }
}
