/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.component.UIInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComponentMessageFactoryTest {
    @Test
    void requiredInputUsesStandardMessage() {
        try (TestFacesContext context = new TestFacesContext()) {
            UIInput input = requiredInput();
            input.validate(context);
            assertFalse(input.isValid());
            FacesMessage message = context.getMessages("name").next();
            assertTrue(message.getSummary().contains("Name"));
            assertTrue(message.getSummary().contains("required"));
            assertEquals(FacesMessage.SEVERITY_ERROR, message.getSeverity());
        }
    }

    @Test
    void requiredInputUsesApplicationMessageBundle() {
        try (TestFacesContext context = new TestFacesContext()) {
            context.getApplication().setMessageBundle("jakarta_faces.jakarta_faces_api.ApplicationMessages");
            UIInput input = requiredInput();
            input.validate(context);
            assertFalse(input.isValid());
            FacesMessage message = context.getMessages("name").next();
            assertEquals("Please enter Name", message.getSummary());
            assertEquals("Name cannot be empty", message.getDetail());
        }
    }

    private static UIInput requiredInput() {
        UIInput input = new UIInput();
        input.setId("name");
        input.setRendererType(null);
        input.getAttributes().put("label", "Name");
        input.setRequired(true);
        input.setSubmittedValue("");
        return input;
    }
}
