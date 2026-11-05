/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIInput;
import jakarta.faces.validator.LengthValidator;
import jakarta.faces.validator.ValidatorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidatorMessageFactoryTest {
    @Test
    void shortValueUsesStandardValidationMessage() {
        try (TestFacesContext context = new TestFacesContext()) {
            FacesMessage message = shortValue(context);
            assertTrue(message.getSummary().contains("Username"));
            assertTrue(message.getSummary().contains("5"));
            assertEquals(FacesMessage.SEVERITY_ERROR, message.getSeverity());
        }
    }

    @Test
    void shortValueUsesApplicationValidationMessage() {
        try (TestFacesContext context = new TestFacesContext()) {
            context.getApplication().setMessageBundle("jakarta_faces.jakarta_faces_api.ApplicationMessages");
            FacesMessage message = shortValue(context);
            assertEquals("Username needs at least 5 characters", message.getSummary());
            assertEquals("Choose a longer Username", message.getDetail());
        }
    }

    private static FacesMessage shortValue(TestFacesContext context) {
        UIInput input = new UIInput();
        input.getAttributes().put("label", "Username");
        LengthValidator validator = new LengthValidator();
        validator.setMinimum(5);
        ValidatorException exception = assertThrows(ValidatorException.class,
                () -> validator.validate(context, input, "abc"));
        return exception.getFacesMessage();
    }
}
