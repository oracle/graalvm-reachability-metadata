/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.enums.Enum;
import io.sundr.deps.org.apache.commons.lang.enums.EnumUtils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class EnumTest {
    private static final String ENUM_CLASS_NAME = "io.sundr.deps.org.apache.commons.lang.enum.Enum";
    private static final String ENUM_INTERNAL_NAME = "io/sundr/deps/org/apache/commons/lang/enum/Enum";
    private static final String ENUM_UTILS_CLASS_NAME = "io.sundr.deps.org.apache.commons.lang.enum.EnumUtils";
    private static final String GENERATED_ENUM_CLASS =
            "io_sundr.sundr_codegen_velocity_nodeps.GeneratedSundrEnumValue";
    private static final String GENERATED_ENUM_INTERNAL_NAME =
            "io_sundr/sundr_codegen_velocity_nodeps/GeneratedSundrEnumValue";

    @Order(1)
    @Test
    public void constructorRegistersDefinedEnumSubclass() throws Exception {
        try {
            ByteArrayClassLoader classLoader = new ByteArrayClassLoader(EnumTest.class.getClassLoader());
            Class<?> generatedEnumClass = classLoader.define(generatedEnumBytes());
            Class.forName(GENERATED_ENUM_CLASS, true, classLoader);

            Class<?> enumUtilsClass = classLoader.loadClass(ENUM_UTILS_CLASS_NAME);
            Method getEnumList = enumUtilsClass.getMethod("getEnumList", Class.class);
            Object enumList = getEnumList.invoke(null, generatedEnumClass);

            assertThat(enumList).isInstanceOf(List.class);
            assertThat((List<?>) enumList).hasSize(1);
            assertThat(((List<?>) enumList).get(0).toString()).isEqualTo("GeneratedSundrEnumValue[alpha]");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Order(2)
    @Test
    public void enumUtilsReturnsEmptyMapForEnumBaseClass() throws Exception {
        ClassLoader classLoader = EnumTest.class.getClassLoader();
        Class<?> enumClass = classLoader.loadClass(ENUM_CLASS_NAME);
        Class<?> enumUtilsClass = classLoader.loadClass(ENUM_UTILS_CLASS_NAME);
        Method getEnumMap = enumUtilsClass.getMethod("getEnumMap", Class.class);

        Object enumMap = getEnumMap.invoke(null, enumClass);

        assertThat(enumMap).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) enumMap).isEmpty();
    }

    @Order(3)
    @Test
    public void enumUtilsReturnsEmptyListForEnumBaseClass() throws Exception {
        ClassLoader classLoader = EnumTest.class.getClassLoader();
        Class<?> enumClass = classLoader.loadClass(ENUM_CLASS_NAME);
        Class<?> enumUtilsClass = classLoader.loadClass(ENUM_UTILS_CLASS_NAME);
        Method getEnumList = enumUtilsClass.getMethod("getEnumList", Class.class);

        Object enumList = getEnumList.invoke(null, enumClass);

        assertThat(enumList).isInstanceOf(List.class);
        assertThat((List<?>) enumList).isEmpty();
    }

    @Order(4)
    @Test
    public void enumUtilsReturnsRegisteredValuesForConcreteEnumsSubclass() {
        SundrEnumValue alpha = SundrEnumValue.ALPHA;

        List<?> enumList = EnumUtils.getEnumList(SundrEnumValue.class);
        Map<?, ?> enumMap = EnumUtils.getEnumMap(SundrEnumValue.class);
        Enum enumByName = EnumUtils.getEnum(SundrEnumValue.class, "alpha");

        assertThat(enumList).hasSize(1);
        assertThat(enumList.get(0)).isSameAs(alpha);
        assertThat(enumMap).hasSize(1);
        assertThat(enumMap.get("alpha")).isSameAs(alpha);
        assertThat(enumByName).isSameAs(alpha);
        assertThat(alpha.getName()).isEqualTo("alpha");
    }

    private static byte[] generatedEnumBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(49);
        out.writeShort(21);
        writeUtf8(out, GENERATED_ENUM_INTERNAL_NAME);
        writeClass(out, 1);
        writeUtf8(out, ENUM_INTERNAL_NAME);
        writeClass(out, 3);
        writeUtf8(out, "ALPHA");
        writeUtf8(out, "L" + GENERATED_ENUM_INTERNAL_NAME + ";");
        writeUtf8(out, "<init>");
        writeUtf8(out, "(Ljava/lang/String;)V");
        writeUtf8(out, "Code");
        writeNameAndType(out, 7, 8);
        writeMethodRef(out, 4, 10);
        writeUtf8(out, "<clinit>");
        writeUtf8(out, "()V");
        writeUtf8(out, "alpha");
        writeString(out, 14);
        writeMethodRef(out, 2, 10);
        writeNameAndType(out, 5, 6);
        writeFieldRef(out, 2, 17);
        writeUtf8(out, "SourceFile");
        writeUtf8(out, "GeneratedSundrEnumValue.java");
        out.writeShort(0x0031);
        out.writeShort(2);
        out.writeShort(4);
        out.writeShort(0);
        out.writeShort(1);
        out.writeShort(0x0019);
        out.writeShort(5);
        out.writeShort(6);
        out.writeShort(0);
        out.writeShort(2);
        writeMethod(out, 0x0002, 7, 8, new byte[] {(byte) 0x2A, (byte) 0x2B, (byte) 0xB7, 0, 11, (byte) 0xB1},
                2, 2);
        writeMethod(out, 0x0008, 12, 13,
                new byte[] {(byte) 0xBB, 0, 2, (byte) 0x59, 0x12, 15, (byte) 0xB7, 0, 16, (byte) 0xB3,
                        0, 18, (byte) 0xB1},
                3, 0);
        out.writeShort(1);
        out.writeShort(19);
        out.writeInt(2);
        out.writeShort(20);
        return bytes.toByteArray();
    }

    private static void writeUtf8(DataOutputStream out, String value) throws IOException {
        out.writeByte(1);
        out.writeUTF(value);
    }

    private static void writeClass(DataOutputStream out, int nameIndex) throws IOException {
        out.writeByte(7);
        out.writeShort(nameIndex);
    }

    private static void writeString(DataOutputStream out, int stringIndex) throws IOException {
        out.writeByte(8);
        out.writeShort(stringIndex);
    }

    private static void writeNameAndType(DataOutputStream out, int nameIndex, int descriptorIndex) throws IOException {
        out.writeByte(12);
        out.writeShort(nameIndex);
        out.writeShort(descriptorIndex);
    }

    private static void writeMethodRef(DataOutputStream out, int classIndex, int nameAndTypeIndex) throws IOException {
        out.writeByte(10);
        out.writeShort(classIndex);
        out.writeShort(nameAndTypeIndex);
    }

    private static void writeFieldRef(DataOutputStream out, int classIndex, int nameAndTypeIndex) throws IOException {
        out.writeByte(9);
        out.writeShort(classIndex);
        out.writeShort(nameAndTypeIndex);
    }

    private static void writeMethod(DataOutputStream out, int accessFlags, int nameIndex, int descriptorIndex,
            byte[] code, int maxStack, int maxLocals) throws IOException {
        out.writeShort(accessFlags);
        out.writeShort(nameIndex);
        out.writeShort(descriptorIndex);
        out.writeShort(1);
        out.writeShort(9);
        out.writeInt(12 + code.length);
        out.writeShort(maxStack);
        out.writeShort(maxLocals);
        out.writeInt(code.length);
        out.write(code);
        out.writeShort(0);
        out.writeShort(0);
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(byte[] classBytes) {
            return defineClass(GENERATED_ENUM_CLASS, classBytes, 0, classBytes.length);
        }
    }

    private static final class SundrEnumValue extends Enum {
        private static final long serialVersionUID = 1L;

        private static final SundrEnumValue ALPHA = new SundrEnumValue("alpha");

        private SundrEnumValue(String name) {
            super(name);
        }
    }
}
