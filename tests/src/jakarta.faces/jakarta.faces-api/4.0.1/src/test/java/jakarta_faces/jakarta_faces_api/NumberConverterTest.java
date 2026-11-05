/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.util.Locale;

import jakarta.faces.component.UIInput;
import jakarta.faces.convert.NumberConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NumberConverterTest {
    @Test
    void formatsAndParsesCurrencyUsingConfiguredCode() {
        try (TestFacesContext context = new TestFacesContext()) {
            NumberConverter converter = new NumberConverter();
            converter.setLocale(Locale.US);
            converter.setType("currency");
            converter.setCurrencyCode("USD");
            converter.setCurrencySymbol("ignored");
            UIInput component = new UIInput();
            assertEquals("$1,234.50", converter.getAsString(context, component, 1234.5));
            Number parsed = (Number) converter.getAsObject(context, component, "$1,234.50");
            assertEquals(1234.5, parsed.doubleValue());
        }
    }
}
