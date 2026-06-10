/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.s9api.HostLanguage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardErrorListenerTest {
    @Test
    void makeAnotherCreatesCleanListenerWithCurrentLogger() {
        StandardLogger logger = new StandardLogger();
        StandardErrorListener listener = new StandardErrorListener();
        listener.setLogger(logger);
        listener.setMaximumNumberOfWarnings(1);
        listener.setStackTraceDetail(0);
        listener.setMaxOrdinaryCharacter(127);

        StandardErrorListener copy = listener.makeAnother(HostLanguage.XSLT);

        assertThat(copy).isNotSameAs(listener);
        assertThat(copy).isInstanceOf(StandardErrorListener.class);
        assertThat(copy.getLogger()).isSameAs(logger);
        assertThat(copy.getMaximumNumberOfWarnings()).isEqualTo(25);
        assertThat(copy.getStackTraceDetail()).isEqualTo(2);
    }
}
