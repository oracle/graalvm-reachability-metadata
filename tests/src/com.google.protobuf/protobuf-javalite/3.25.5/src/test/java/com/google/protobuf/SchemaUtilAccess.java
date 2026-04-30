/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

public final class SchemaUtilAccess {
    private SchemaUtilAccess() {
    }

    public static Object unknownFieldSetFullSchema() {
        return SchemaUtil.unknownFieldSetFullSchema();
    }

    public static Object mapDefaultEntry(Class<?> messageClass, String fieldName) {
        return SchemaUtil.getMapDefaultEntry(messageClass, fieldName);
    }
}
