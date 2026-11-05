/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIInput;
import jakarta.faces.convert.ConverterException;
import jakarta.faces.convert.IntegerConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConvertMessageFactoryTest {
    @Test
    void invalidIntegerUsesStandardConversionMessage() {
        try (TestFacesContext context = new TestFacesContext()) {
            FacesMessage message = invalidInteger(context);
            assertTrue(message.getSummary().contains("Quantity"));
            assertTrue(message.getSummary().contains("many"));
            assertTrue(message.getDetail().contains("2147483647"));
            assertEquals(FacesMessage.SEVERITY_ERROR, message.getSeverity());
        }
    }

    @Test
    void invalidIntegerUsesApplicationConversionMessage() {
        try (TestFacesContext context = new TestFacesContext()) {
            context.getApplication().setMessageBundle("jakarta_faces.jakarta_faces_api.ApplicationMessages");
            FacesMessage message = invalidInteger(context);
            assertEquals("Quantity needs digits, not many", message.getSummary());
            assertEquals("For Quantity, enter a whole number", message.getDetail());
        }
    }

    private static FacesMessage invalidInteger(TestFacesContext context) {
        UIInput input = new UIInput();
        input.getAttributes().put("label", "Quantity");
        IntegerConverter converter = new IntegerConverter();
        ConverterException exception = assertThrows(ConverterException.class,
                () -> converter.getAsObject(context, input, "many"));
        return exception.getFacesMessage();
    }
}
