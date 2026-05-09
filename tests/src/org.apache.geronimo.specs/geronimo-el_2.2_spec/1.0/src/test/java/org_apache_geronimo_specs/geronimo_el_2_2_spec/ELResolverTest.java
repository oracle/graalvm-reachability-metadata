/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import java.util.HashMap;
import java.util.Locale;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.MapELResolver;
import javax.el.PropertyNotWritableException;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ELResolverTest {

    @Test
    void loadsLocalStringsBundleWhenFormattingResolverErrorMessage() {
        ELContext context = new LocaleAwareELContext(Locale.US);
        ELResolver resolver = new MapELResolver(true);
        HashMap<String, String> map = new HashMap<>();

        assertThatThrownBy(() -> resolver.setValue(context, map, "name", "value"))
                .isInstanceOf(PropertyNotWritableException.class)
                .satisfies(throwable -> assertThat(throwable.getMessage()).isNotBlank());
        assertThat(context.isPropertyResolved()).isTrue();
    }

    private static final class LocaleAwareELContext extends ELContext {
        LocaleAwareELContext(Locale locale) {
            setLocale(locale);
        }

        @Override
        public ELResolver getELResolver() {
            return null;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }
}
