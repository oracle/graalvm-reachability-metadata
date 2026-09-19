/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.security.annotation.CreatedBy;
import io.micronaut.security.annotation.RunAs;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.annotation.UpdatedBy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for the runtime security annotation contracts. */
@Timeout(55)
public class MicronautSecurityAnnotationsTest {
    @Test
    void securedRolesAreAvailableOnTypesAndMethodsAtRuntime() throws NoSuchMethodException {
        Secured inheritedSecurity = SpecializedOperations.class.getAnnotation(Secured.class);
        Method audit = SecuredOperations.class.getMethod("audit");
        Secured methodSecurity = audit.getAnnotation(Secured.class);

        assertThat(inheritedSecurity.value()).containsExactly("ROLE_OPERATOR", "isAuthenticated()");
        assertThat(methodSecurity.value()).containsExactly("ROLE_AUDITOR");
        assertThat(new SpecializedOperations().audit()).isEqualTo("audit-complete");
    }

    @Test
    void runAsConfigurationRetainsIdentityRolesAttributesAndDefaults() throws NoSuchMethodException {
        RunAs inheritedRunAs = SpecializedOperations.class.getAnnotation(RunAs.class);
        RunAs methodRunAs = SecuredOperations.class.getMethod("audit").getAnnotation(RunAs.class);

        assertThat(inheritedRunAs.value()).containsExactly("ROLE_SUPPORT");
        assertThat(inheritedRunAs.name()).isEqualTo("service-account");
        assertThat(inheritedRunAs.appendRoles()).isFalse();
        assertThat(inheritedRunAs.appendAttributes()).isFalse();
        assertThat(inheritedRunAs.attributes()).hasSize(2);
        assertThat(inheritedRunAs.attributes()[0].key()).isEqualTo("tenant");
        assertThat(inheritedRunAs.attributes()[0].value()).isEqualTo("north");
        assertThat(inheritedRunAs.attributes()[1].key()).isEqualTo("channel");
        assertThat(inheritedRunAs.attributes()[1].value()).isEqualTo("batch");

        assertThat(methodRunAs.roles()).containsExactly("ROLE_AUDITOR");
        assertThat(methodRunAs.name()).isEmpty();
        assertThat(methodRunAs.appendRoles()).isTrue();
        assertThat(methodRunAs.attributes()).isEmpty();
        assertThat(methodRunAs.appendAttributes()).isTrue();
    }

    @Test
    void auditingMarkersAreVisibleOnEntityPropertiesAtRuntime() throws NoSuchFieldException, NoSuchMethodException {
        Field creator = AuditedRecord.class.getField("creator");
        Method updater = AuditedRecord.class.getMethod("getUpdater");

        assertThat(creator.getAnnotation(CreatedBy.class)).isNotNull();
        assertThat(creator.getAnnotation(UpdatedBy.class)).isNull();
        assertThat(updater.getAnnotation(UpdatedBy.class)).isNotNull();
        assertThat(updater.getAnnotation(CreatedBy.class)).isNull();
    }

    @Secured({"ROLE_OPERATOR", "isAuthenticated()"})
    @RunAs(
            value = "ROLE_SUPPORT",
            name = "service-account",
            appendRoles = false,
            attributes = {
                @RunAs.Attribute(key = "tenant", value = "north"),
                @RunAs.Attribute(key = "channel", value = "batch")
            },
            appendAttributes = false)
    public static class SecuredOperations {
        @Secured("ROLE_AUDITOR")
        @RunAs(roles = "ROLE_AUDITOR")
        public String audit() {
            return "audit-complete";
        }
    }

    public static class SpecializedOperations extends SecuredOperations {}

    public static final class AuditedRecord {
        @CreatedBy
        public String creator;

        private String updater;

        @UpdatedBy
        public String getUpdater() {
            return updater;
        }
    }
}
