/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.bind.v2.model.annotation.RuntimeInlineAnnotationReader;
import com.sun.xml.bind.v2.model.impl.RuntimeModelBuilder;
import com.sun.xml.bind.v2.model.nav.Navigator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;

final class NavigatorSupport {
    private NavigatorSupport() {
    }

    @SuppressWarnings("rawtypes")
    static Navigator<Type, Class, Field, Method> navigator() {
        return new RuntimeModelBuilder(null, new RuntimeInlineAnnotationReader(),
                Collections.emptyMap(), "").getNavigator();
    }
}
