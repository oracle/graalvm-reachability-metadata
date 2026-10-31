/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_servlet.jstl;

import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import javax.servlet.jsp.jstl.fmt.LocalizationContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class LocalizationContextTest {
    @Test
    void suppliesTheBundleAndLocaleForMessageFormatting() throws IOException {
        ResourceBundle bundle = new PropertyResourceBundle(new StringReader("price=Total: {0,number,#,##0.00}"));
        LocalizationContext context = new LocalizationContext(bundle, Locale.GERMANY);

        MessageFormat format = new MessageFormat(context.getResourceBundle().getString("price"), context.getLocale());
        assertEquals("Total: 1.234,50", format.format(new Object[]{1234.5}));
        assertSame(bundle, context.getResourceBundle());
        assertEquals(Locale.GERMANY, context.getLocale());
    }

    @Test
    void supportsAnEmptyLocalizationContext() {
        LocalizationContext context = new LocalizationContext();

        assertNull(context.getResourceBundle());
        assertNull(context.getLocale());
    }
}
