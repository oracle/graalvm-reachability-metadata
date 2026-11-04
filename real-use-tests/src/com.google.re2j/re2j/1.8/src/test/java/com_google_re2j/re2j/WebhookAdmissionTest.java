/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the whole-input validation subsystem to admit well-formed webhook deliveries.
 */
public class WebhookAdmissionTest {

    @Test
    @Timeout(60)
    void admitsOnlyDeliveriesWithAValidEnvelope() {
        WebhookAdmission admission = new WebhookAdmission();

        int admitted = admission.countAdmitted(new String[] {
                "delivery-id=evt-2048;attempt=2;signature=0123456789abcdef",
                "delivery-id=evt-2049;attempt=4;signature=0123456789abcdef",
                "delivery-id=evt-2050;attempt=1;signature=0123456789abcde"
        });

        assertEquals(1, admitted);
    }

    /** Application admission policy that validates a complete webhook envelope before queuing it. */
    private static final class WebhookAdmission {

        private static final String DELIVERY =
                "delivery-id=[a-z]+-[0-9]+;attempt=[1-3];signature=[a-f0-9]{16}";

        int countAdmitted(String[] deliveries) {
            int admitted = 0;
            for (String delivery : deliveries) {
                if (Pattern.matches(DELIVERY, delivery)) {
                    admitted++;
                }
            }
            return admitted;
        }
    }
}
