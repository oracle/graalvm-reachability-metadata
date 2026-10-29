/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_jtds.jtds;

import net.sourceforge.jtds.jdbc.CharsetInfo;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Charset mappings initialize only once, before any other driver test uses them.
@Order(1)
public class CharsetInfoAnonymous1Test {
    @Test
    void resolvesCharsetsWhenTheContextLoaderCannotFindDriverResources() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(new ClassLoader(null) { });
            CharsetInfo charset = CharsetInfo.getCharset("UTF8");
            assertThat(charset).isNotNull();
            assertThat(charset.getCharset()).isEqualTo("UTF-8");
            assertThat(charset.isWideChars()).isTrue();
            assertThat(CharsetInfo.getCharsetForLCID(1033).getCharset()).isEqualTo("Cp1252");
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
