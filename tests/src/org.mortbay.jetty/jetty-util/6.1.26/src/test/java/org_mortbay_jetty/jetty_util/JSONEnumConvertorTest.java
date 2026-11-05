/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONEnumConvertor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class JSONEnumConvertorTest {
    @Test
    public void roundTripsEnumWithClassAndValue() {
        JSON json = new JSON();
        json.addConvertor(Status.class, new JSONEnumConvertor(true));
        String document = json.toJSON(Status.READY);
        assertSame(Status.READY, json.fromJSON(document));
    }

    @Test
    public void rendersEnumAsAStringWhenTypeInformationIsDisabled() {
        JSON json = new JSON();
        json.addConvertor(Status.class, new JSONEnumConvertor());
        assertEquals("\"WAITING\"", json.toJSON(Status.WAITING));
    }

    public enum Status {
        READY, WAITING
    }
}
