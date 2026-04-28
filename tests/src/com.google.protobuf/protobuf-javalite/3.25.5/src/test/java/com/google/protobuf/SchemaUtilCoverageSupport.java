/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf;

import java.io.IOException;

public final class SchemaUtilCoverageSupport {
    private SchemaUtilCoverageSupport() {
    }

    public static Object fullUnknownFieldSchema() {
        return SchemaUtil.unknownFieldSetFullSchema();
    }

    public static Object structFieldsDefaultEntry() {
        return SchemaUtil.getMapDefaultEntry(Struct.class, "fields");
    }
}

final class UnknownFieldSetSchema extends UnknownFieldSchema<Object, Object> {
    public UnknownFieldSetSchema() {
    }

    @Override
    boolean shouldDiscardUnknownFields(Reader reader) {
        return false;
    }

    @Override
    void addVarint(Object fields, int number, long value) {
    }

    @Override
    void addFixed32(Object fields, int number, int value) {
    }

    @Override
    void addFixed64(Object fields, int number, long value) {
    }

    @Override
    void addLengthDelimited(Object fields, int number, ByteString value) {
    }

    @Override
    void addGroup(Object fields, int number, Object subFieldSet) {
    }

    @Override
    Object newBuilder() {
        return new Object();
    }

    @Override
    Object toImmutable(Object fields) {
        return fields;
    }

    @Override
    void setToMessage(Object message, Object fields) {
    }

    @Override
    Object getFromMessage(Object message) {
        return null;
    }

    @Override
    Object getBuilderFromMessage(Object message) {
        return newBuilder();
    }

    @Override
    void setBuilderToMessage(Object message, Object builder) {
    }

    @Override
    void makeImmutable(Object message) {
    }

    @Override
    void writeTo(Object unknownFields, Writer writer) throws IOException {
    }

    @Override
    void writeAsMessageSetTo(Object unknownFields, Writer writer) throws IOException {
    }

    @Override
    Object merge(Object destination, Object source) {
        return source;
    }

    @Override
    int getSerializedSizeAsMessageSet(Object message) {
        return 0;
    }

    @Override
    int getSerializedSize(Object unknowns) {
        return 0;
    }
}
