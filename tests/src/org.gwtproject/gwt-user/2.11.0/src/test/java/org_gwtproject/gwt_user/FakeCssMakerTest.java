/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.junit.FakeCssMaker;
import com.google.gwt.resources.client.CssResource;

import org.junit.jupiter.api.Test;

public class FakeCssMakerTest {
    @Test
    void createsCssResourceProxyReturningMethodNames() {
        SampleCss css = FakeCssMaker.create(SampleCss.class);

        assertThat(css.primaryButton()).isEqualTo("primaryButton");
        assertThat(css.getName()).isEqualTo("getName");
        assertThat(css.getText()).isEqualTo("getText");
        assertThat(css.ensureInjected()).isFalse();
    }

    public interface SampleCss extends CssResource {
        String primaryButton();
    }
}
