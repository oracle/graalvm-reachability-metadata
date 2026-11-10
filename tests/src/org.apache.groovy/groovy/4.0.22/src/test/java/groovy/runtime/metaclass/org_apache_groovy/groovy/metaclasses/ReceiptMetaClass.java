/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package groovy.runtime.metaclass.org_apache_groovy.groovy.metaclasses;

import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;

/// Consumer extension in the namespace required by Groovy's custom-metaclass lookup convention.
public class ReceiptMetaClass extends MetaClassImpl {
    public ReceiptMetaClass(MetaClassRegistry registry, Class<?> type) {
        super(registry, type);
    }

    @Override
    public Object invokeMethod(Object object, String name, Object[] arguments) {
        if ("archiveReference".equals(name)) {
            return "archived:" + super.invokeMethod(object, "reference", arguments);
        }
        return super.invokeMethod(object, name, arguments);
    }
}
