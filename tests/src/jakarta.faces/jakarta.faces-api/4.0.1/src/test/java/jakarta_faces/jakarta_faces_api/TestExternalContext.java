/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.util.HashMap;
import java.util.Map;

import jakarta.faces.context.ExternalContextWrapper;

final class TestExternalContext extends ExternalContextWrapper {
    private final Map<String, Object> applicationMap = new HashMap<>();
    private final Map<String, Object> requestMap = new HashMap<>();

    TestExternalContext() {
        super(null);
    }

    @Override
    public Map<String, Object> getApplicationMap() {
        return applicationMap;
    }

    @Override
    public Map<String, Object> getRequestMap() {
        return requestMap;
    }

    @Override
    public String getInitParameter(String name) {
        return null;
    }

    @Override
    public Map<String, String> getInitParameterMap() {
        return Map.of();
    }
}
