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
    public void createsStyleBundleProxyAndAppliesConfiguredStyle() {
        MemoryStyleSource source = new MemoryStyleSource();
        StyleSource previousSource = Styler.getSource();
        source.set("messages", "warning", "bold,fg:red");
        Styler.setSource(source);

        try {
            MessageStyles styles = Styler.bundle(MessageStyles.class);
            assertEquals(MessageStyles.class.getName(), styles.toString());

            AttributedString warning = styles.warning("danger");
            assertEquals("danger", warning.toString());
            assertEquals(
                    AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.RED),
                    warning.styleAt(0));
        } finally {
            Styler.setSource(previousSource);
        }
    }

    @StyleGroup("messages")
    public interface MessageStyles extends StyleBundle {
        AttributedString warning(String message);
    }
}
