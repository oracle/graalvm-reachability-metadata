/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

import java.lang.annotation.ElementType;
import javax.validation.Path;
import javax.validation.TraversableResolver;

public class XmlTraversableResolver implements TraversableResolver {
    @Override
    public boolean isReachable(
            Object traversableObject,
            Path.Node traversableProperty,
            Class<?> rootBeanType,
            Path pathToTraversableObject,
            ElementType elementType) {
        return true;
    }

    @Override
    public boolean isCascadable(
            Object traversableObject,
            Path.Node traversableProperty,
            Class<?> rootBeanType,
            Path pathToTraversableObject,
            ElementType elementType) {
        return true;
    }
}
