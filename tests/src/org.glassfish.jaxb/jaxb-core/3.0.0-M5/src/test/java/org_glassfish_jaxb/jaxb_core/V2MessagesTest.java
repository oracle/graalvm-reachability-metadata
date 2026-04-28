/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.jaxb.core.v2.Messages;
import org.junit.jupiter.api.Test;

public class V2MessagesTest {
    @Test
    public void loadsV2MessageBundleWhenFormattingMessage() {
        String message = Messages.ERROR_LOADING_CLASS.format("SampleType", "sample/jaxb.index");

        assertThat(message)
                .contains("SampleType")
                .contains("sample/jaxb.index")
                .contains("error loading class");
    }

    @Test
    public void formatsNoArgumentV2MessageThroughToString() {
        String message = Messages.INVALID_TYPE_IN_MAP.toString();

        assertThat(message).contains("Map contains a wrong type");
    }
}
