/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.util.CachingDateFormatter;
import ch.qos.logback.core.util.OptionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionHelperTest {
    @Test
    void instantiateByClassNameUsesNoArgumentConstructor() throws Exception {
        ClassLoader classLoader = OptionHelperTest.class.getClassLoader();

        Object instance = OptionHelper.instantiateByClassName(
                ContextBase.class.getName(), Context.class, classLoader);

        assertThat(instance).isInstanceOf(ContextBase.class);
        Context context = (Context) instance;
        context.setName("option-helper-context");
        assertThat(context.getName()).isEqualTo("option-helper-context");
    }

    @Test
    void instantiateByClassNameAndParameterUsesMatchingConstructor() throws Exception {
        ClassLoader classLoader = OptionHelperTest.class.getClassLoader();

        Object instance = OptionHelper.instantiateByClassNameAndParameter(
                CachingDateFormatter.class.getName(),
                CachingDateFormatter.class,
                classLoader,
                String.class,
                "HH");

        assertThat(instance).isInstanceOf(CachingDateFormatter.class);
        CachingDateFormatter formatter = (CachingDateFormatter) instance;
        assertThat(formatter.format(0L)).hasSize(2);
    }

    @Test
    void instantiateClassWithSuperclassRestrictionUsesNoArgumentConstructor() throws Exception {
        Object instance = OptionHelper.instantiateClassWithSuperclassRestriction(
                ContextBase.class, Context.class);

        assertThat(instance).isInstanceOf(ContextBase.class);
    }
}
