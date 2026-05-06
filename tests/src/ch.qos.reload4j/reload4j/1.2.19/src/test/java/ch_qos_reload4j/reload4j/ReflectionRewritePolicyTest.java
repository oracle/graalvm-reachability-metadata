/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.rewrite.ReflectionRewritePolicy;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionRewritePolicyTest {
    private static final Logger LOGGER = Logger.getLogger("reload4j.reflection-rewrite-policy");

    @Test
    void rewritesBeanMessageIntoLoggingEventProperties() {
        ReflectionRewritePolicy policy = new ReflectionRewritePolicy();
        MessageBean message = new MessageBean("rewritten message", "alice", 7);
        LoggingEvent event = new LoggingEvent(
                ReflectionRewritePolicyTest.class.getName(),
                LOGGER,
                Level.INFO,
                message,
                null);
        event.setProperty("account", "original account");
        event.setProperty("existing", "kept value");

        LoggingEvent rewritten = policy.rewrite(event);

        assertThat(rewritten).isNotSameAs(event);
        assertThat(rewritten.getMessage()).isEqualTo("rewritten message");
        assertThat(rewritten.getLevel()).isSameAs(Level.INFO);
        assertThat(rewritten.getLoggerName()).isEqualTo(LOGGER.getName());
        assertThat(rewritten.getProperty("account")).isEqualTo("alice");
        assertThat(rewritten.getProperty("attempt")).isEqualTo("7");
        assertThat(rewritten.getProperty("existing")).isEqualTo("kept value");
        assertThat(message.getterCalls()).containsEntry("message", 1);
        assertThat(message.getterCalls()).containsEntry("account", 1);
        assertThat(message.getterCalls()).containsEntry("attempt", 1);
    }

    public static final class MessageBean {
        private final String message;
        private final String account;
        private final int attempt;
        private final Map<String, Integer> getterCalls = new HashMap<>();

        public MessageBean(String message, String account, int attempt) {
            this.message = message;
            this.account = account;
            this.attempt = attempt;
        }

        public String getMessage() {
            recordGetterCall("message");
            return message;
        }

        public String getAccount() {
            recordGetterCall("account");
            return account;
        }

        public int getAttempt() {
            recordGetterCall("attempt");
            return attempt;
        }

        Map<String, Integer> getterCalls() {
            return getterCalls;
        }

        private void recordGetterCall(String getterName) {
            Integer previousCalls = getterCalls.get(getterName);
            if (previousCalls == null) {
                getterCalls.put(getterName, 1);
            } else {
                getterCalls.put(getterName, previousCalls + 1);
            }
        }
    }
}
