/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.NullHandler;
import ognl.Ognl;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's null-handling subsystem for rendering an incomplete support request.
 *
 * The support portal supplies a safe contact placeholder when an optional request contact has
 * not been entered, so its display expression can still render a useful value.
 */
public class SupportRequestNullHandlingTest {
    private NullHandler previousNullHandler;

    @BeforeEach
    void registerSupportRequestNullHandler() throws OgnlException {
        previousNullHandler = OgnlRuntime.getNullHandler(SupportRequest.class);
        OgnlRuntime.setNullHandler(SupportRequest.class, new SupportRequestNullHandler());
    }

    @AfterEach
    void restoreDefaultNullHandler() {
        OgnlRuntime.setNullHandler(SupportRequest.class, previousNullHandler);
    }

    @Test
    @Timeout(60)
    void rendersFallbackContactForRequestWithNoContact() throws OgnlException {
        SupportRequest request = new SupportRequest("SR-2048", null);
        Map context = Ognl.createDefaultContext(request);

        Object contactEmail = Ognl.getValue("contact.email", context, request);

        assertThat(contactEmail).isEqualTo("support@example.test");
    }

    public static final class SupportRequestNullHandler implements NullHandler {
        @Override
        public Object nullMethodResult(
                Map context, Object target, String methodName, Object[] arguments) {
            return null;
        }

        @Override
        public Object nullPropertyValue(Map context, Object target, Object property) {
            if ("contact".equals(property)) {
                return new Contact("support@example.test");
            }
            return null;
        }
    }

    public static final class SupportRequest {
        private final String reference;
        private final Contact contact;

        private SupportRequest(String reference, Contact contact) {
            this.reference = reference;
            this.contact = contact;
        }

        public String getReference() {
            return reference;
        }

        public Contact getContact() {
            return contact;
        }
    }

    public static final class Contact {
        private final String email;

        private Contact(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }
}
