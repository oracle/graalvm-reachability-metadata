/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import java.util.Map;

import jakarta_xml_bind.jakarta_xml_bind_api.support.WrongTypeReturnValue;

public final class DefaultWrongTypeContextFactory {
    private DefaultWrongTypeContextFactory() {
    }

    public static Object createContext(Class<?>[] classes, Map<?, ?> properties) {
        return new WrongTypeReturnValue();
    }
}
