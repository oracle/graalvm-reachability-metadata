/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import org.junit.jupiter.api.Test;

/**
 * Field-mask set algebra for response selection is the authorization subsystem exercised here.
 *
 * An API gateway intersects a client's requested fields with the fields allowed by a product
 * policy, records fields it could not serve, and adds an identifier required by the audit sink.
 */
public class FieldMaskSelectionPolicyTest {

    @Test
    void computesAuthorizedResponseSelection() {
        FieldMask requested =
                FieldMaskUtil.fromJsonString("account.name,account.email,orders.id,orders.total");
        FieldMask allowed = FieldMaskUtil.fromString("account,orders.id,orders.status");

        FieldMask selected = FieldMaskUtil.intersection(requested, allowed);
        FieldMask unavailable = FieldMaskUtil.subtract(requested, allowed);
        FieldMask audited =
                FieldMaskUtil.union(selected, FieldMaskUtil.fromString("request_id"));

        assertEquals("account.email,account.name,orders.id", FieldMaskUtil.toString(selected));
        assertEquals("orders.total", FieldMaskUtil.toString(unavailable));
        assertEquals(
                "account.email,account.name,orders.id,requestId", FieldMaskUtil.toJsonString(audited));
        assertEquals(
                "account.email,account.name,orders.id,request_id", FieldMaskUtil.toString(audited));
    }
}
