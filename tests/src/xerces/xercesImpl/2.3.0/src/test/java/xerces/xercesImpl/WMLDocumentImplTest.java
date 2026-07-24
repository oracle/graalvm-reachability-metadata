/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package xerces.xercesImpl;

import org.apache.wml.WMLCardElement;
import org.apache.wml.dom.WMLDocumentImpl;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises WML element creation through the document's public DOM API.
 * §FS-repository-functional-spec.5.2
 */
public class WMLDocumentImplTest {
    @Test
    void createsSpecializedCardElement() {
        Document document = new WMLDocumentImpl(null);

        Element element = document.createElement("card");

        assertThat(element).isInstanceOf(WMLCardElement.class);
        WMLCardElement card = (WMLCardElement) element;
        card.setTitle("Welcome");
        card.setOrdered(true);

        assertThat(card.getTitle()).isEqualTo("Welcome");
        assertThat(card.getOrdered()).isTrue();
    }
}
