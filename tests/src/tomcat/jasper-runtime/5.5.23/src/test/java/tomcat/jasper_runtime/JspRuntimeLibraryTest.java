/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.JspRuntimeLibrary;
import org.junit.jupiter.api.Test;

import java.beans.PropertyEditorManager;
import java.beans.PropertyEditorSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class JspRuntimeLibraryTest {
    @Test
    void introspectHelperSetsSimpleBeanProperty() throws Exception {
        SampleBean bean = new SampleBean();

        JspRuntimeLibrary.introspecthelper(bean, "count", "42", null, null, false);

        assertThat(bean.getCount()).isEqualTo(42);
    }

    @Test
    void introspectHelperSetsStringArrayBeanPropertyFromServletRequest() throws Exception {
        SampleBean bean = new SampleBean();
        Map<String, String[]> parameters = new HashMap<>();
        parameters.put("tags", new String[] {"jsp", "tomcat"});
        ServletRequest request = new ParameterServletRequest(parameters);

        JspRuntimeLibrary.introspecthelper(bean, "tags", null, request, "tags", false);

        assertThat(bean.getTags()).containsExactly("jsp", "tomcat");
    }

    @Test
    void beanInfoPropertyEditorCreatesConvertedValue() throws Exception {
        Object value = JspRuntimeLibrary.getValueFromBeanInfoPropertyEditor(
                EditedValue.class, "value", "jasper", EditedValueEditor.class);

        assertThat(value).isInstanceOf(EditedValue.class);
        assertThat(((EditedValue) value).getText()).isEqualTo("JASPER");
    }

    @Test
    void createTypedArraySetsEverySupportedArrayType() throws Exception {
        ArrayBean bean = new ArrayBean();

        createTypedArray(bean, "integers", new String[] {"1", "2"}, Integer.class);
        createTypedArray(bean, "bytes", new String[] {"3", "4"}, Byte.class);
        createTypedArray(bean, "booleans", new String[] {"true", "false"}, Boolean.class);
        createTypedArray(bean, "shorts", new String[] {"5", "6"}, Short.class);
        createTypedArray(bean, "longs", new String[] {"7", "8"}, Long.class);
        createTypedArray(bean, "doubles", new String[] {"1.25", "2.5"}, Double.class);
        createTypedArray(bean, "floats", new String[] {"3.5", "4.5"}, Float.class);
        createTypedArray(bean, "characters", new String[] {"a", "b"}, Character.class);
        createTypedArray(bean, "primitiveInts", new String[] {"9", "10"}, int.class);
        createTypedArray(bean, "primitiveBytes", new String[] {"11", "12"}, byte.class);
        createTypedArray(bean, "primitiveBooleans", new String[] {"true", "false"}, boolean.class);
        createTypedArray(bean, "primitiveShorts", new String[] {"13", "14"}, short.class);
        createTypedArray(bean, "primitiveLongs", new String[] {"15", "16"}, long.class);
        createTypedArray(bean, "primitiveDoubles", new String[] {"5.5", "6.5"}, double.class);
        createTypedArray(bean, "primitiveFloats", new String[] {"7.5", "8.5"}, float.class);
        createTypedArray(bean, "primitiveChars", new String[] {"x", "y"}, char.class);

        assertThat(bean.getIntegers()).containsExactly(1, 2);
        assertThat(bean.getBytes()).containsExactly((byte) 3, (byte) 4);
        assertThat(bean.getBooleans()).containsExactly(true, false);
        assertThat(bean.getShorts()).containsExactly((short) 5, (short) 6);
        assertThat(bean.getLongs()).containsExactly(7L, 8L);
        assertThat(bean.getDoubles()).containsExactly(1.25, 2.5);
        assertThat(bean.getFloats()).containsExactly(3.5f, 4.5f);
        assertThat(bean.getCharacters()).containsExactly('a', 'b');
        assertThat(bean.getPrimitiveInts()).containsExactly(9, 10);
        assertThat(bean.getPrimitiveBytes()).containsExactly((byte) 11, (byte) 12);
        assertThat(bean.getPrimitiveBooleans()).containsExactly(true, false);
        assertThat(bean.getPrimitiveShorts()).containsExactly((short) 13, (short) 14);
        assertThat(bean.getPrimitiveLongs()).containsExactly(15L, 16L);
        assertThat(bean.getPrimitiveDoubles()).containsExactly(5.5, 6.5);
        assertThat(bean.getPrimitiveFloats()).containsExactly(7.5f, 8.5f);
        assertThat(bean.getPrimitiveChars()).containsExactly('x', 'y');
    }

    @Test
    void createTypedArrayUsesExplicitAndRegisteredPropertyEditors() throws Exception {
        ArrayBean bean = new ArrayBean();
        Method explicitEditorMethod = JspRuntimeLibrary.getWriteMethod(ArrayBean.class, "editorConvertedValues");
        JspRuntimeLibrary.createTypedArray("editorConvertedValues", bean, explicitEditorMethod,
                new String[] {"17", "18"}, Integer.class, IntegerArrayEditor.class);

        try {
            PropertyEditorManager.registerEditor(ManagerConvertedValue.class, IntegerArrayEditor.class);
            Method managerEditorMethod = JspRuntimeLibrary.getWriteMethod(ArrayBean.class, "managerConvertedValues");
            JspRuntimeLibrary.createTypedArray("managerConvertedValues", bean, managerEditorMethod,
                    new String[] {"19", "20"}, ManagerConvertedValue.class, null);
        } finally {
            PropertyEditorManager.registerEditor(ManagerConvertedValue.class, null);
        }

        assertThat(bean.getEditorConvertedValues()).containsExactly(17, 18);
        assertThat(bean.getManagerConvertedValues()).containsExactly(19, 20);
    }

    private static void createTypedArray(ArrayBean bean, String propertyName, String[] values,
            Class<?> componentType) throws Exception {
        Method method = JspRuntimeLibrary.getWriteMethod(ArrayBean.class, propertyName);
        JspRuntimeLibrary.createTypedArray(propertyName, bean, method, values, componentType, null);
    }

    public static class SampleBean {
        private int count;
        private String[] tags;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }
    }

    public static class EditedValue {
        private final String text;

        public EditedValue(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class EditedValueEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            setValue(new EditedValue(text.toUpperCase(Locale.ROOT)));
        }
    }

    public static class IntegerArrayEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            setValue(Integer.valueOf(text));
        }
    }

    public static class ManagerConvertedValue {
    }

    public static class ArrayBean {
        private Integer[] integers;
        private Byte[] bytes;
        private Boolean[] booleans;
        private Short[] shorts;
        private Long[] longs;
        private Double[] doubles;
        private Float[] floats;
        private Character[] characters;
        private int[] primitiveInts;
        private byte[] primitiveBytes;
        private boolean[] primitiveBooleans;
        private short[] primitiveShorts;
        private long[] primitiveLongs;
        private double[] primitiveDoubles;
        private float[] primitiveFloats;
        private char[] primitiveChars;
        private Object[] editorConvertedValues;
        private Object[] managerConvertedValues;

        public Integer[] getIntegers() {
            return integers;
        }

        public void setIntegers(Integer[] integers) {
            this.integers = integers;
        }

        public Byte[] getBytes() {
            return bytes;
        }

        public void setBytes(Byte[] bytes) {
            this.bytes = bytes;
        }

        public Boolean[] getBooleans() {
            return booleans;
        }

        public void setBooleans(Boolean[] booleans) {
            this.booleans = booleans;
        }

        public Short[] getShorts() {
            return shorts;
        }

        public void setShorts(Short[] shorts) {
            this.shorts = shorts;
        }

        public Long[] getLongs() {
            return longs;
        }

        public void setLongs(Long[] longs) {
            this.longs = longs;
        }

        public Double[] getDoubles() {
            return doubles;
        }

        public void setDoubles(Double[] doubles) {
            this.doubles = doubles;
        }

        public Float[] getFloats() {
            return floats;
        }

        public void setFloats(Float[] floats) {
            this.floats = floats;
        }

        public Character[] getCharacters() {
            return characters;
        }

        public void setCharacters(Character[] characters) {
            this.characters = characters;
        }

        public int[] getPrimitiveInts() {
            return primitiveInts;
        }

        public void setPrimitiveInts(int[] primitiveInts) {
            this.primitiveInts = primitiveInts;
        }

        public byte[] getPrimitiveBytes() {
            return primitiveBytes;
        }

        public void setPrimitiveBytes(byte[] primitiveBytes) {
            this.primitiveBytes = primitiveBytes;
        }

        public boolean[] getPrimitiveBooleans() {
            return primitiveBooleans;
        }

        public void setPrimitiveBooleans(boolean[] primitiveBooleans) {
            this.primitiveBooleans = primitiveBooleans;
        }

        public short[] getPrimitiveShorts() {
            return primitiveShorts;
        }

        public void setPrimitiveShorts(short[] primitiveShorts) {
            this.primitiveShorts = primitiveShorts;
        }

        public long[] getPrimitiveLongs() {
            return primitiveLongs;
        }

        public void setPrimitiveLongs(long[] primitiveLongs) {
            this.primitiveLongs = primitiveLongs;
        }

        public double[] getPrimitiveDoubles() {
            return primitiveDoubles;
        }

        public void setPrimitiveDoubles(double[] primitiveDoubles) {
            this.primitiveDoubles = primitiveDoubles;
        }

        public float[] getPrimitiveFloats() {
            return primitiveFloats;
        }

        public void setPrimitiveFloats(float[] primitiveFloats) {
            this.primitiveFloats = primitiveFloats;
        }

        public char[] getPrimitiveChars() {
            return primitiveChars;
        }

        public void setPrimitiveChars(char[] primitiveChars) {
            this.primitiveChars = primitiveChars;
        }

        public Object[] getEditorConvertedValues() {
            return editorConvertedValues;
        }

        public void setEditorConvertedValues(Object[] editorConvertedValues) {
            this.editorConvertedValues = editorConvertedValues;
        }

        public Object[] getManagerConvertedValues() {
            return managerConvertedValues;
        }

        public void setManagerConvertedValues(Object[] managerConvertedValues) {
            this.managerConvertedValues = managerConvertedValues;
        }
    }

    private static class ParameterServletRequest implements ServletRequest {
        private final Map<String, String[]> parameters;

        ParameterServletRequest(Map<String, String[]> parameters) {
            this.parameters = parameters;
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public Enumeration getAttributeNames() {
            return Collections.enumeration(Collections.emptyList());
        }

        @Override
        public String getCharacterEncoding() {
            return null;
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return null;
        }

        @Override
        public String getParameter(String name) {
            String[] values = getParameterValues(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public Enumeration getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }

        @Override
        public Map getParameterMap() {
            return parameters;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public int getServerPort() {
            return 80;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        @Override
        public String getRemoteHost() {
            return "localhost";
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public Enumeration getLocales() {
            return Collections.enumeration(Collections.singletonList(Locale.ROOT));
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        @Override
        public String getRealPath(String path) {
            return path;
        }

        @Override
        public int getRemotePort() {
            return 12345;
        }

        @Override
        public String getLocalName() {
            return "localhost";
        }

        @Override
        public String getLocalAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getLocalPort() {
            return 80;
        }
    }
}
