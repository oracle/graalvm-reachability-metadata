/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.component.UIInput;
import jakarta.faces.validator.LengthValidator;
import jakarta.faces.validator.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AttachedObjectListHolderTest {
    @Test
    void returnsValidatorsInRegistrationOrderAndRemovesThem() {
        try (TestFacesContext context = new TestFacesContext()) {
            UIInput input = new UIInput();
            LengthValidator first = new LengthValidator(12, 2);
            LengthValidator second = new LengthValidator(20, 1);
            input.addValidator(first);
            input.addValidator(second);
            assertEquals(Validator[].class, input.getValidators().getClass());
            assertArrayEquals(new Validator[]{first, second}, input.getValidators());
            input.removeValidator(first);
            assertArrayEquals(new Validator[]{second}, input.getValidators());
            input.removeValidator(second);
            assertEquals(0, input.getValidators().length);
        }
    }
}
