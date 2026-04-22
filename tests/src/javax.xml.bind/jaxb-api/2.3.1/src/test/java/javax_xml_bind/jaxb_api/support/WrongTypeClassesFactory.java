/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_xml_bind.jaxb_api.support;

import java.util.Map;

public final class WrongTypeClassesFactory {
    private WrongTypeClassesFactory() {
    }

    public static Object createContext(Class<?>[] classes, Map<?, ?> properties) {
        return new WrongTypeReturnValue();
    }
}
