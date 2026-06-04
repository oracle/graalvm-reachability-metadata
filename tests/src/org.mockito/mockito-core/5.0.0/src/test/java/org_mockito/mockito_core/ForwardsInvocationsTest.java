/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardsInvocationsTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void delegatesToObjectWithMatchingMethodOnDifferentType() {
        final MessageGateway gateway =
                Mockito.mock(
                        MessageGateway.class,
                        AdditionalAnswers.delegatesTo(new MessageDelegate("delegate")));

        assertThat(gateway.message("Mockito", 2)).isEqualTo("delegate:Mockito:2");
    }

    public interface MessageGateway {
        String message(String name, int count);
    }

    public static final class MessageDelegate {
        private final String prefix;

        public MessageDelegate(String prefix) {
            this.prefix = prefix;
        }

        public String message(String name, int count) {
            return prefix + ":" + name + ":" + count;
        }
    }
}
