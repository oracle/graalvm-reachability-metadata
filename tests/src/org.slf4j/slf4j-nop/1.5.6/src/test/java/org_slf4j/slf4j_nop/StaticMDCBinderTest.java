/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_nop;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.helpers.NOPMakerAdapter;
import org.slf4j.impl.StaticMDCBinder;
import org.slf4j.spi.MDCAdapter;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticMDCBinderTest {

    @Test
    void staticMdcBinderProvidesTheNopMdcAdapterUsedBySlf4j() {
        StaticMDCBinder binder = StaticMDCBinder.SINGLETON;
        MDCAdapter binderAdapter = binder.getMDCA();
        MDCAdapter apiAdapter = MDC.getMDCAdapter();

        assertThat(binderAdapter).isInstanceOf(NOPMakerAdapter.class);
        assertThat(apiAdapter).isInstanceOf(NOPMakerAdapter.class);
        assertThat(binder.getMDCAdapterClassStr()).isEqualTo(NOPMakerAdapter.class.getName());
        assertThat(apiAdapter.getClass().getName()).isEqualTo(binder.getMDCAdapterClassStr());
        assertThat(StaticMDCBinder.SINGLETON).isSameAs(binder);
    }
}
