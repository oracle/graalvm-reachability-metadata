/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.cglib.proxy.InterfaceMaker;
import org.springframework.core.io.Resource;

public class InterfaceMakerTest {

    @Test
    void addsPublicMethodsFromSpringInterface() {
        InterfaceMaker interfaceMaker = new InterfaceMaker();

        assertThatCode(() -> interfaceMaker.add(Resource.class)).doesNotThrowAnyException();
    }
}
