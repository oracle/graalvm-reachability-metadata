/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.Constants;
import org.springframework.transaction.TransactionDefinition;

import static org.junit.jupiter.api.Assertions.*;

public class ConstantsTest {
    @Test
    void resolvesTransactionConstantsByNameAndPrefix() {
        Constants constants = new Constants(TransactionDefinition.class);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED,
                constants.asNumber("propagation_required").intValue());
        assertTrue(constants.getNames("ISOLATION_").contains("ISOLATION_SERIALIZABLE"));
        assertEquals("PROPAGATION_REQUIRES_NEW", constants.toCode(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW, "PROPAGATION_"));
    }
}
