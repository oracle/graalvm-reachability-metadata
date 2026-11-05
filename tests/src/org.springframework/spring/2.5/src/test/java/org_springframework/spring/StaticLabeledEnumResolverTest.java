/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.core.enums.StaticLabeledEnum;
import org.springframework.core.enums.StaticLabeledEnumResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class StaticLabeledEnumResolverTest {
    public static class Status extends StaticLabeledEnum {
        public static final Status READY = new Status(1, "Ready");
        public static final Status SENT = new Status(2, "Sent");

        private Status(int code, String label) {
            super(code, label);
        }
    }

    @Test
    void discoversStaticEnumerationsAndLooksUpCodeAndLabel() {
        StaticLabeledEnumResolver resolver = new StaticLabeledEnumResolver();
        assertEquals(2, resolver.getLabeledEnumSet(Status.class).size());
        assertSame(Status.READY, resolver.getLabeledEnumByCode(Status.class, Short.valueOf((short) 1)));
        assertSame(Status.SENT, resolver.getLabeledEnumByLabel(Status.class, "sent"));
    }
}
