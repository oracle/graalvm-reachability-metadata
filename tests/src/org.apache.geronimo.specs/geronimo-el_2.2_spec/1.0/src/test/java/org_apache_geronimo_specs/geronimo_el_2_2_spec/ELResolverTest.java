/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_el_2_2_spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.MapELResolver;
import javax.el.PropertyNotWritableException;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

public class ELResolverTest {
    @Test
    void readOnlyMapResolverFormatsLocalizedNotWritableMessage() {
        MapELResolver resolver = new MapELResolver(true);
        SimpleELContext context = new SimpleELContext();
        context.setLocale(Locale.US);
        Map<String, String> values = new HashMap<>();
        values.put("existing", "value");

        assertThatThrownBy(() -> resolver.setValue(context, values, "existing", "updated"))
                .isInstanceOf(PropertyNotWritableException.class)
                .satisfies(throwable -> assertThat(throwable.getMessage()).isNotBlank());
        assertThat(context.isPropertyResolved()).isTrue();
        assertThat(values).containsEntry("existing", "value");
    }

    static final class SimpleELContext extends ELContext {
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
