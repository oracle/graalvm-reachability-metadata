/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package groovy.runtime.metaclass.org_apache_groovy.groovy.metaclasses;

import groovy.lang.DelegatingMetaClass;
import groovy.lang.MetaClass;

/// Consumer extension in the namespace required by Groovy's custom-metaclass lookup convention.
public class PriorityInvoiceMetaClass extends DelegatingMetaClass {
    public PriorityInvoiceMetaClass(MetaClass delegate) {
        super(delegate);
    }

    @Override
    public Object invokeMethod(Object object, String name, Object[] arguments) {
        if ("priorityReference".equals(name)) {
            return "priority:" + super.invokeMethod(object, "reference", arguments);
        }
        return super.invokeMethod(object, name, arguments);
    }
}
