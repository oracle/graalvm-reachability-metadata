/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.util.Locale;

import jakarta.faces.component.UIComponentBase;
import jakarta.faces.component.UIInput;
import jakarta.faces.convert.NumberConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StateHolderSaverTest {
    @Test
    void restoresAttachedConverterAndItsSavedConfiguration() {
        try (TestFacesContext context = new TestFacesContext()) {
            NumberConverter original = new NumberConverter();
            original.setLocale(Locale.US);
            original.setPattern("0000.00");
            original.setGroupingUsed(false);
            Object state = UIComponentBase.saveAttachedState(context, original);
            NumberConverter restored = (NumberConverter) UIComponentBase.restoreAttachedState(context, state);
            assertNotSame(original, restored);
            assertEquals("0000.00", restored.getPattern());
            assertEquals("0012.50", restored.getAsString(context, new UIInput(), 12.5));
        }
    }

    @Test
    void omitsTransientAttachedConverters() {
        try (TestFacesContext context = new TestFacesContext()) {
            NumberConverter converter = new NumberConverter();
            converter.setTransient(true);
            Object state = UIComponentBase.saveAttachedState(context, converter);
            assertNull(UIComponentBase.restoreAttachedState(context, state));
        }
    }
}
