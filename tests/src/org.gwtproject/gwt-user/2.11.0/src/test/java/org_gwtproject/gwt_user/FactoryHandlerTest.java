/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.validation.client.impl.ConstraintOrigin;
import com.google.web.bindery.autobean.shared.AutoBeanFactory;
import com.google.web.bindery.autobean.shared.impl.EnumMap;
import com.google.web.bindery.autobean.vm.AutoBeanFactorySource;

import org.junit.jupiter.api.Test;

public class FactoryHandlerTest {
    @Test
    void factoryProxyDispatchesObjectMethodsToHandler() {
        AutoBeanFactory factory = AutoBeanFactorySource.create(AutoBeanFactory.class);

        assertThat(factory.toString()).contains("FactoryHandler");
    }

    @Test
    void factoryProxyMapsEnumsToAndFromWireTokens() {
        AutoBeanFactory factory = AutoBeanFactorySource.create(AutoBeanFactory.class);
        EnumMap enumMap = (EnumMap) factory;

        assertThat(enumMap.getToken(ConstraintOrigin.DEFINED_LOCALLY))
                .isEqualTo("DEFINED_LOCALLY");
        assertThat(enumMap.getEnum(ConstraintOrigin.class, "DEFINED_IN_HIERARCHY"))
                .isEqualTo(ConstraintOrigin.DEFINED_IN_HIERARCHY);
    }
}
