/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_log4j12;

import org.junit.jupiter.api.Test;
import org.slf4j.impl.Log4jMDCAdapter;
import org.slf4j.impl.StaticMDCBinder;
import org.slf4j.spi.MDCAdapter;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticMDCBinderTest {

    @Test
    void singletonProvidesTheLog4jMdcAdapterClassName() {
        StaticMDCBinder binder = StaticMDCBinder.SINGLETON;
        MDCAdapter adapter = binder.getMDCA();

        assertThat(adapter).isInstanceOf(Log4jMDCAdapter.class);
        assertThat(binder.getMDCAdapterClassStr()).isEqualTo(Log4jMDCAdapter.class.getName());
    }
}
