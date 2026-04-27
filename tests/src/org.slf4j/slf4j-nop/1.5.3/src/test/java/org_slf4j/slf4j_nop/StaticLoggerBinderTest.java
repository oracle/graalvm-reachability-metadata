/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_nop;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticLoggerBinderTest {

    @Test
    void staticLoggerBinderProvidesTheNopLoggerFactoryUsedBySlf4j() {
        StaticLoggerBinder binder = StaticLoggerBinder.SINGLETON;
        ILoggerFactory binderFactory = binder.getLoggerFactory();
        ILoggerFactory apiFactory = LoggerFactory.getILoggerFactory();
        Logger firstLogger = binderFactory.getLogger("first");
        Logger secondLogger = binderFactory.getLogger("second");

        assertThat(apiFactory).isSameAs(binderFactory);
        assertThat(binder.getLoggerFactoryClassStr()).isEqualTo(binderFactory.getClass().getName());
        assertThat(firstLogger).isSameAs(secondLogger);
        assertThat(StaticLoggerBinder.SINGLETON).isSameAs(binder);
    }
}
