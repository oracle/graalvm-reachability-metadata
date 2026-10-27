/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.style.MemoryStyleSource;
import org.jline.style.StyleBundle;
import org.jline.style.StyleBundle.StyleGroup;
import org.jline.style.StyleSource;
import org.jline.style.Styler;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StyleBundleInvocationHandlerTest {

    @Test
    public void createsBundleProxyAndResolvesConfiguredStyle() {
        MemoryStyleSource source = new MemoryStyleSource();
        StyleSource originalSource = Styler.getSource();
        source.set("alerts", "failure", "bold,fg:red");
        Styler.setSource(source);

        try {
            AlertStyles styles = Styler.bundle(AlertStyles.class);
            assertEquals(AlertStyles.class.getName(), styles.toString());

            AttributedString failure = styles.failure("operation failed");
            assertEquals("operation failed", failure.toString());
            assertEquals(
                    AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.RED),
                    failure.styleAt(0));
        } finally {
            Styler.setSource(originalSource);
        }
    }

    @StyleGroup("alerts")
    public interface AlertStyles extends StyleBundle {
        AttributedString failure(String message);
    }
}
