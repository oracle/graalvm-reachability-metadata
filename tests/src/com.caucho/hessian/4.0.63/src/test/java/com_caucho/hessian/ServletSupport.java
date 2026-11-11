/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_caucho.hessian;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

public final class ServletSupport implements ServletConfig {
    private final Map<String, String> parameters;

    public ServletSupport(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String getServletName() {
        return "snapshots";
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public String getInitParameter(String name) {
        return parameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }
}
