/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.spi.ObjectFactory;

public final class NamingSupport {
    private NamingSupport() {
    }

    public static class GreetingFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object object, Name name, Context context, Hashtable<?, ?> environment) {
            return "resolved-greeting";
        }
    }
}
