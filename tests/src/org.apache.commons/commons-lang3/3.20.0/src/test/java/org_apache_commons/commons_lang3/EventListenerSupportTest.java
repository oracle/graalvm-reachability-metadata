/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class EventListenerSupportTest {

    public interface AuditListener {

        void onAudit(String message);
    }

    public static final class SerializableAuditListener implements AuditListener, Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> events = new ArrayList<>();

        public SerializableAuditListener(final String name) {
            this.name = name;
        }

        @Override
        public void onAudit(final String message) {
            events.add(name + ":" + message);
        }

        public List<String> events() {
            return events;
        }
    }

    public static final class NonSerializableAuditListener implements AuditListener {

        private final String name;
        private final List<String> events = new ArrayList<>();

        public NonSerializableAuditListener(final String name) {
            this.name = name;
        }

        @Override
        public void onAudit(final String message) {
            events.add(name + ":" + message);
        }

        public List<String> events() {
            return events;
        }
    }
}
