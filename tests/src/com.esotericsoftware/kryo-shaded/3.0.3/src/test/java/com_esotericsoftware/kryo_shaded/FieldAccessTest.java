/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.FieldAccess;
import org.junit.jupiter.api.Test;

public class FieldAccessTest {
    @Test
    void createsReusableFieldAccessForPublicFieldsDeclaredAcrossHierarchy() {
        FieldAccess access = FieldAccess.get(Subject.class);
        Subject subject = new Subject();

        access.set(subject, "name", "sample");
        access.setInt(subject, access.getIndex("count"), 42);
        access.setBoolean(subject, access.getIndex("active"), true);
        access.setLong(subject, access.getIndex("createdAt"), 123456789L);

        assertThat(access.getFieldCount()).isEqualTo(4);
        assertThat(access.getFieldNames()).containsExactly("name", "count", "active", "createdAt");
        assertThat(access.getFieldTypes()).containsExactly(String.class, int.class, boolean.class, long.class);
        assertThat(access.get(subject, "name")).isEqualTo("sample");
        assertThat(access.getString(subject, access.getIndex("name"))).isEqualTo("sample");
        assertThat(access.getInt(subject, access.getIndex("count"))).isEqualTo(42);
        assertThat(access.getBoolean(subject, access.getIndex("active"))).isTrue();
        assertThat(access.getLong(subject, access.getIndex("createdAt"))).isEqualTo(123456789L);
        assertThat(subject.name).isEqualTo("sample");
        assertThat(subject.count).isEqualTo(42);
        assertThat(subject.active).isTrue();
        assertThat(subject.createdAt).isEqualTo(123456789L);
    }

    public static class SubjectBase {
        public long createdAt;

        public SubjectBase() {
        }
    }

    public static class Subject extends SubjectBase {
        public String name;
        public int count;
        public boolean active;
        private String privateValue;
        public static String staticValue;

        public Subject() {
        }
    }

    public static class SubjectFieldAccess extends FieldAccess {
        public SubjectFieldAccess() {
        }

        @Override
        public void set(Object instance, int fieldIndex, Object value) {
            Subject subject = (Subject) instance;
            switch (fieldIndex) {
                case 0:
                    subject.name = (String) value;
                    return;
                case 1:
                    subject.count = (Integer) value;
                    return;
                case 2:
                    subject.active = (Boolean) value;
                    return;
                case 3:
                    subject.createdAt = (Long) value;
                    return;
                default:
                    throw invalidIndex(fieldIndex);
            }
        }

        @Override
        public void setBoolean(Object instance, int fieldIndex, boolean value) {
            if (fieldIndex != 2) {
                throw invalidIndex(fieldIndex);
            }
            ((Subject) instance).active = value;
        }

        @Override
        public void setByte(Object instance, int fieldIndex, byte value) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public void setShort(Object instance, int fieldIndex, short value) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public void setInt(Object instance, int fieldIndex, int value) {
            if (fieldIndex != 1) {
                throw invalidIndex(fieldIndex);
            }
            ((Subject) instance).count = value;
        }

        @Override
        public void setLong(Object instance, int fieldIndex, long value) {
            if (fieldIndex != 3) {
                throw invalidIndex(fieldIndex);
            }
            ((Subject) instance).createdAt = value;
        }

        @Override
        public void setDouble(Object instance, int fieldIndex, double value) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public void setFloat(Object instance, int fieldIndex, float value) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public void setChar(Object instance, int fieldIndex, char value) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public Object get(Object instance, int fieldIndex) {
            Subject subject = (Subject) instance;
            switch (fieldIndex) {
                case 0:
                    return subject.name;
                case 1:
                    return subject.count;
                case 2:
                    return subject.active;
                case 3:
                    return subject.createdAt;
                default:
                    throw invalidIndex(fieldIndex);
            }
        }

        @Override
        public String getString(Object instance, int fieldIndex) {
            if (fieldIndex != 0) {
                throw invalidIndex(fieldIndex);
            }
            return ((Subject) instance).name;
        }

        @Override
        public char getChar(Object instance, int fieldIndex) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public boolean getBoolean(Object instance, int fieldIndex) {
            if (fieldIndex != 2) {
                throw invalidIndex(fieldIndex);
            }
            return ((Subject) instance).active;
        }

        @Override
        public byte getByte(Object instance, int fieldIndex) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public short getShort(Object instance, int fieldIndex) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public int getInt(Object instance, int fieldIndex) {
            if (fieldIndex != 1) {
                throw invalidIndex(fieldIndex);
            }
            return ((Subject) instance).count;
        }

        @Override
        public long getLong(Object instance, int fieldIndex) {
            if (fieldIndex != 3) {
                throw invalidIndex(fieldIndex);
            }
            return ((Subject) instance).createdAt;
        }

        @Override
        public double getDouble(Object instance, int fieldIndex) {
            throw invalidIndex(fieldIndex);
        }

        @Override
        public float getFloat(Object instance, int fieldIndex) {
            throw invalidIndex(fieldIndex);
        }

        private static IllegalArgumentException invalidIndex(int fieldIndex) {
            return new IllegalArgumentException("Invalid field index: " + fieldIndex);
        }
    }
}
