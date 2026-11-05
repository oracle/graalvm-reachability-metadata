/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.lang.Closure;

/// A builder closure written in Java: the nested body that Groovy builders call for an element.
public final class BuilderBody extends Closure<Object> {
    private static final long serialVersionUID = 1L;

    private final transient Runnable body;

    public BuilderBody(Object owner, Runnable body) {
        super(owner);
        this.body = body;
    }

    public Object doCall() {
        body.run();
        return null;
    }
}
