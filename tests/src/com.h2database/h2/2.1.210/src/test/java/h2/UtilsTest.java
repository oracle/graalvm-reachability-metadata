/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.Utils;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    @Test
    void invokesStaticAndInstanceMethodsThroughUtilsReflectionHelpers() throws Exception {
        Object staticResult = Utils.callStaticMethod(UtilsTest.class.getName() + ".staticMessage", "H2");
        assertThat(staticResult).isEqualTo("static:H2");

        Object instance = Utils.newInstance(UtilsTest.class.getName());
        assertThat(instance).isInstanceOf(UtilsTest.class);

        Object instanceResult = Utils.callMethod(instance, "instanceMessage", "covered", 2);
        assertThat(instanceResult).isEqualTo("covered-covered");
    }

    @Test
    void loadsBundledWebConsoleResourceThroughUtilsResourceLoader() throws Exception {
        byte[] resource = Utils.getResource("/org/h2/server/web/res/_text_en.prop");

        assertThat(resource).isNotNull();
        assertThat(resource.length).isGreaterThan(100);
    }

    @Test
    void scalesValueForAvailableMemory() {
        assertThat(Utils.scaleForAvailableMemory(1)).isGreaterThanOrEqualTo(0);
    }

    public static String staticMessage(String value) {
        return "static:" + value;
    }

    public String instanceMessage(String value, int repetitions) {
        return String.join("-", Collections.nCopies(repetitions, value));
    }
}
