/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.glassfish.grizzly.attributes;

/**
 * Package-private bridge for testing builder initialization without relying on
 * global static initialization timing in the native test image.
 */
public final class AttributeBuilderTestAccess {
    private AttributeBuilderTestAccess() {
    }

    public static AttributeBuilder initBuilder() {
        return AttributeBuilderInitializer.initBuilder();
    }
}
