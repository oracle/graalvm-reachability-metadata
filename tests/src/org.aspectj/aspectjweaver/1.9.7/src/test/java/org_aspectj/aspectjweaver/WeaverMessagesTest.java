/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.weaver.WeaverMessages;
import org.junit.jupiter.api.Test;

public class WeaverMessagesTest {
    @Test
    void formatsMessagesFromWeaverResourceBundle() {
        String directMessage = WeaverMessages.format(WeaverMessages.ARGS_IN_DECLARE);
        String singleArgumentMessage = WeaverMessages.format(WeaverMessages.ABSTRACT_POINTCUT, "trackedPointcut");
        String twoArgumentMessage = WeaverMessages.format(WeaverMessages.CANT_FIND_POINTCUT, "missingPointcut", "ExampleAspect");

        assertThat(directMessage).isEqualTo("args() pointcut designator cannot be used in declare statement");
        assertThat(singleArgumentMessage).isEqualTo("trackedPointcut is abstract");
        assertThat(twoArgumentMessage).isEqualTo("can't find pointcut 'missingPointcut' on ExampleAspect");
    }
}
