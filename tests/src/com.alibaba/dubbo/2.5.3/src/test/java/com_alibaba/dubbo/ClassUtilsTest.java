/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.compiler.support.ClassUtils;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    @Test
    void resolvesAndInstantiatesClassesAndSearchesCompatibleMethods() throws Exception {
        assertThat(ClassUtils.forName("java.lang.String[]")).isEqualTo(String[].class);
        assertThat(ClassUtils.newInstance("java.util.ArrayList")).isInstanceOf(ArrayList.class);
        assertThat(ClassUtils.getSizeMethod(HasLength.class)).isEqualTo("length()");
        assertThat(ClassUtils.getSizeMethod(HasGetSize.class)).isEqualTo("getSize()");
        assertThat(ClassUtils.getSizeMethod(HasGetLength.class)).isEqualTo("getLength()");
        assertThat(ClassUtils.searchMethod(Sink.class, "put", new Class<?>[] {String.class}))
                .hasName("put");
    }

    public static class HasLength {
        public int length() {
            return 1;
        }
    }

    public static class HasGetSize {
        public int getSize() {
            return 1;
        }
    }

    public static class HasGetLength {
        public int getLength() {
            return 1;
        }
    }

    public static class Sink {
        public void put(CharSequence value) {}
    }
}
