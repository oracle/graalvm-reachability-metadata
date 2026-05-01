/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.introspection.MethodMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodMapTest {
    @Test
    void resolvesPrimitiveFormalParametersFromWrapperArguments() throws Exception {
        MethodMap methodMap = new MethodMap();
        Method setMethod = BitSet.class.getMethod("set", int.class, boolean.class);
        methodMap.add(setMethod);

        Method resolvedMethod = methodMap.find("set", new Object[] {Integer.valueOf(3), Boolean.TRUE});

        assertThat(resolvedMethod).isSameAs(setMethod);
    }

    @Test
    void choosesMostSpecificApplicableOverload() throws Exception {
        MethodMap methodMap = new MethodMap();
        Method appendObjectMethod = StringBuilder.class.getMethod("append", Object.class);
        Method appendStringMethod = StringBuilder.class.getMethod("append", String.class);
        methodMap.add(appendObjectMethod);
        methodMap.add(appendStringMethod);

        Method resolvedMethod = methodMap.find("append", new Object[] {"plexus"});

        assertThat(resolvedMethod).isSameAs(appendStringMethod);
    }

    @Test
    void treatsNullAsApplicableOnlyToReferenceParameters() throws Exception {
        MethodMap methodMap = new MethodMap();
        Method appendStringMethod = StringBuilder.class.getMethod("append", String.class);
        Method appendIntegerMethod = StringBuilder.class.getMethod("append", int.class);
        methodMap.add(appendStringMethod);
        methodMap.add(appendIntegerMethod);

        Method resolvedMethod = methodMap.find("append", new Object[] {null});

        assertThat(resolvedMethod).isSameAs(appendStringMethod);
    }

    @Test
    void returnsNullWhenNoMethodCanAcceptArguments() throws Exception {
        MethodMap methodMap = new MethodMap();
        Method appendStringMethod = StringBuilder.class.getMethod("append", String.class);
        methodMap.add(appendStringMethod);

        Method resolvedMethod = methodMap.find("append", new Object[] {Integer.valueOf(7)});

        assertThat(resolvedMethod).isNull();
    }
}
