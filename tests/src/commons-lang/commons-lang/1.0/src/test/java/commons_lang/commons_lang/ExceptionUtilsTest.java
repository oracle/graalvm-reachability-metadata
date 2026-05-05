/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_lang.commons_lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class ExceptionUtilsTest {

    @Test
    public void getCauseUsesWellKnownCauseMethodNames() {
        IllegalStateException root = new IllegalStateException("root cause");
        RuntimeException wrapper = new RuntimeException("wrapper", root);

        Throwable cause = ExceptionUtils.getCause(wrapper);

        assertThat(cause).isSameAs(root);
    }

    @Test
    public void getCauseFallsBackToPublicDetailField() {
        IllegalArgumentException root = new IllegalArgumentException("field cause");
        DetailBackedException wrapper = new DetailBackedException(root);

        Throwable cause = ExceptionUtils.getCause(wrapper);

        assertThat(cause).isSameAs(root);
    }

    public static class DetailBackedException extends Exception {
        public Throwable detail;

        public DetailBackedException(Throwable detail) {
            super("detail backed");
            this.detail = detail;
        }
    }
}
