/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.jline;

import org.jline.builtins.Styles;
import org.jline.utils.AttributedStyle;
import org.jline.utils.StyleResolver;
import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class StylesTest {

    @Test
    public void resolvesDefaultDirectoryStyle() {
        StyleResolver resolver = Styles.lsStyle();

        assertTrue(Styles.isStylePattern("di=1;91:fi="));
        assertNotEquals(AttributedStyle.DEFAULT, resolver.resolve(".di"));
    }
}
