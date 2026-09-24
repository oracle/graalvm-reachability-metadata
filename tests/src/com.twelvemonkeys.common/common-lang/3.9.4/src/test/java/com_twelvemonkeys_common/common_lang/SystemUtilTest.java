/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twelvemonkeys_common.common_lang;

import com.twelvemonkeys.lang.SystemUtil;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class SystemUtilTest {
    @Test
    void loadsExactAndSuffixGuessedResources() throws Exception {
        Properties exact = SystemUtil.loadProperties("common-lang/exact.properties");
        Properties guessed = SystemUtil.loadProperties("common-lang/guessed");
        Properties xmlFallback = SystemUtil.loadProperties("common-lang/xml-only");

        assertThat(exact.getProperty("source")).isEqualTo("exact");
        assertThat(guessed.getProperty("source")).isEqualTo("properties");
        assertThat(xmlFallback.getProperty("source")).isEqualTo("xml");
    }

    @Test
    void clonesPrimitiveArraysAndCloneableObjects() throws Exception {
        int[] original = {1, 2, 3};
        int[] arrayCopy = (int[]) SystemUtil.clone(original);
        CloneableValue objectCopy = (CloneableValue) SystemUtil.clone(new CloneableValue("copy"));

        assertThat(arrayCopy).containsExactly(1, 2, 3).isNotSameAs(original);
        assertThat(objectCopy.getValue()).isEqualTo("copy");
    }

    @Test
    void discoversClassesFieldsAndMethods() {
        String className = PublicApi.class.getName();

        assertThat(SystemUtil.isClassAvailable(className, SystemUtilTest.class)).isTrue();
        assertThat(SystemUtil.isFieldAvailable(className, "VALUE", SystemUtilTest.class)).isTrue();
        assertThat(SystemUtil.isMethodAvailable(className, "ping", new Class[0], SystemUtilTest.class)).isTrue();
    }

    public static class CloneableValue implements Cloneable {
        private final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public CloneableValue clone() {
            try {
                return (CloneableValue) super.clone();
            } catch (CloneNotSupportedException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    public static class PublicApi {
        public static final String VALUE = "available";

        public static String ping() {
            return "pong";
        }
    }
}
