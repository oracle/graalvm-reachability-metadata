/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jcr.jcr;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.jcr.AccessDeniedException;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.LoginException;
import javax.jcr.MergeException;
import javax.jcr.NamespaceException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyType;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.observation.Event;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.version.OnParentVersionAction;
import javax.jcr.version.VersionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JcrTest {

    @Test
    void simpleCredentialsProtectConstructorPasswordAndManageAttributes() {
        char[] password = new char[] {'s', 'e', 'c', 'r', 'e', 't'};
        SimpleCredentials credentials = new SimpleCredentials("content-admin", password);
        password[0] = 'X';

        assertThat(credentials.getUserID()).isEqualTo("content-admin");
        assertThat(credentials.getPassword()).containsExactly('s', 'e', 'c', 'r', 'e', 't');
        assertThat(credentials.getAttribute("missing")).isNull();
        assertThat(credentials.getAttributeNames()).isEmpty();

        credentials.setAttribute("workspace", "default");
        credentials.setAttribute("readOnly", Boolean.FALSE);

        assertThat(credentials.getAttribute("workspace")).isEqualTo("default");
        assertThat(credentials.getAttribute("readOnly")).isEqualTo(Boolean.FALSE);
        assertThat(credentials.getAttributeNames()).containsExactlyInAnyOrder("workspace", "readOnly");

        credentials.removeAttribute("workspace");
        assertThat(credentials.getAttribute("workspace")).isNull();
        assertThat(credentials.getAttributeNames()).containsExactly("readOnly");

        credentials.setAttribute("readOnly", null);
        assertThat(credentials.getAttribute("readOnly")).isNull();
        assertThat(credentials.getAttributeNames()).isEmpty();

        assertThatThrownBy(() -> credentials.setAttribute(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name cannot be null");
    }

    @Test
    void propertyTypeMapsEveryJcrTypeBetweenIntegerAndName() {
        PropertyTypeCase[] cases = new PropertyTypeCase[] {
                new PropertyTypeCase(PropertyType.STRING, PropertyType.TYPENAME_STRING),
                new PropertyTypeCase(PropertyType.BINARY, PropertyType.TYPENAME_BINARY),
                new PropertyTypeCase(PropertyType.LONG, PropertyType.TYPENAME_LONG),
                new PropertyTypeCase(PropertyType.DOUBLE, PropertyType.TYPENAME_DOUBLE),
                new PropertyTypeCase(PropertyType.DATE, PropertyType.TYPENAME_DATE),
                new PropertyTypeCase(PropertyType.BOOLEAN, PropertyType.TYPENAME_BOOLEAN),
                new PropertyTypeCase(PropertyType.NAME, PropertyType.TYPENAME_NAME),
                new PropertyTypeCase(PropertyType.PATH, PropertyType.TYPENAME_PATH),
                new PropertyTypeCase(PropertyType.REFERENCE, PropertyType.TYPENAME_REFERENCE),
                new PropertyTypeCase(PropertyType.UNDEFINED, PropertyType.TYPENAME_UNDEFINED)
        };

        for (PropertyTypeCase propertyTypeCase : cases) {
            assertThat(PropertyType.nameFromValue(propertyTypeCase.value())).isEqualTo(propertyTypeCase.name());
            assertThat(PropertyType.valueFromName(propertyTypeCase.name())).isEqualTo(propertyTypeCase.value());
        }

        assertThat(PropertyType.TYPENAME_STRING).isEqualTo("String");
        assertThat(PropertyType.TYPENAME_BINARY).isEqualTo("Binary");
        assertThat(PropertyType.TYPENAME_LONG).isEqualTo("Long");
        assertThat(PropertyType.TYPENAME_DOUBLE).isEqualTo("Double");
        assertThat(PropertyType.TYPENAME_DATE).isEqualTo("Date");
        assertThat(PropertyType.TYPENAME_BOOLEAN).isEqualTo("Boolean");
        assertThat(PropertyType.TYPENAME_NAME).isEqualTo("Name");
        assertThat(PropertyType.TYPENAME_PATH).isEqualTo("Path");
        assertThat(PropertyType.TYPENAME_REFERENCE).isEqualTo("Reference");
        assertThat(PropertyType.TYPENAME_UNDEFINED).isEqualTo("undefined");

        assertThatThrownBy(() -> PropertyType.nameFromValue(42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown type: 42");
        assertThatThrownBy(() -> PropertyType.valueFromName("string"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown type: string");
        assertThatThrownBy(() -> PropertyType.valueFromName(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void onParentVersionActionMapsEveryActionBetweenIntegerAndName() {
        OnParentVersionActionCase[] cases = new OnParentVersionActionCase[] {
                new OnParentVersionActionCase(OnParentVersionAction.COPY, OnParentVersionAction.ACTIONNAME_COPY),
                new OnParentVersionActionCase(OnParentVersionAction.VERSION, OnParentVersionAction.ACTIONNAME_VERSION),
                new OnParentVersionActionCase(OnParentVersionAction.INITIALIZE,
                        OnParentVersionAction.ACTIONNAME_INITIALIZE),
                new OnParentVersionActionCase(OnParentVersionAction.COMPUTE, OnParentVersionAction.ACTIONNAME_COMPUTE),
                new OnParentVersionActionCase(OnParentVersionAction.IGNORE, OnParentVersionAction.ACTIONNAME_IGNORE),
                new OnParentVersionActionCase(OnParentVersionAction.ABORT, OnParentVersionAction.ACTIONNAME_ABORT)
        };

        for (OnParentVersionActionCase actionCase : cases) {
            assertThat(OnParentVersionAction.nameFromValue(actionCase.value())).isEqualTo(actionCase.name());
            assertThat(OnParentVersionAction.valueFromName(actionCase.name())).isEqualTo(actionCase.value());
        }

        assertThat(Arrays.stream(cases).map(OnParentVersionActionCase::name))
                .containsExactly("COPY", "VERSION", "INITIALIZE", "COMPUTE", "IGNORE", "ABORT");
        assertThatThrownBy(() -> OnParentVersionAction.nameFromValue(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown on-version action: 99");
        assertThatThrownBy(() -> OnParentVersionAction.valueFromName("copy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown on-version action: copy");
        assertThatThrownBy(() -> OnParentVersionAction.valueFromName(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void repositoryExceptionCombinesOwnMessageWithRootCause() throws Exception {
        IllegalStateException rootCause = new IllegalStateException("repository offline");
        RepositoryException exception = new RepositoryException("login failed", rootCause);

        assertThat(exception).hasMessage("login failed: repository offline");
        assertThat(exception.getLocalizedMessage()).isEqualTo("login failed: repository offline: repository offline");
        assertThat(exception.getCause()).isSameAs(rootCause);

        assertThat(new RepositoryException(rootCause)).hasMessage("repository offline");
        assertThat(new RepositoryException("only message"))
                .hasMessage("only message")
                .hasNoCause();
        RepositoryException emptyException = new RepositoryException();
        assertThat(emptyException.getMessage()).isNull();
        assertThat(emptyException).hasNoCause();

        ByteArrayOutputStream printStreamBytes = new ByteArrayOutputStream();
        exception.printStackTrace(new PrintStream(printStreamBytes, true, StandardCharsets.UTF_8.name()));
        assertThat(printStreamBytes.toString(StandardCharsets.UTF_8.name()))
                .contains("javax.jcr.RepositoryException: login failed: repository offline")
                .contains("java.lang.IllegalStateException: repository offline");

        ByteArrayOutputStream printWriterBytes = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(printWriterBytes, true, StandardCharsets.UTF_8);
        exception.printStackTrace(printWriter);
        assertThat(printWriterBytes.toString(StandardCharsets.UTF_8.name()))
                .contains("javax.jcr.RepositoryException: login failed: repository offline")
                .contains("java.lang.IllegalStateException: repository offline");
    }

    @Test
    void repositoryExceptionSubclassesExposeConsistentConstructors() {
        RepositoryExceptionFactoryCase[] cases = new RepositoryExceptionFactoryCase[] {
                new RepositoryExceptionFactoryCase("AccessDeniedException", AccessDeniedException::new),
                new RepositoryExceptionFactoryCase("InvalidItemStateException", InvalidItemStateException::new),
                new RepositoryExceptionFactoryCase("InvalidSerializedDataException", InvalidSerializedDataException::new),
                new RepositoryExceptionFactoryCase("ItemExistsException", ItemExistsException::new),
                new RepositoryExceptionFactoryCase("ItemNotFoundException", ItemNotFoundException::new),
                new RepositoryExceptionFactoryCase("LockException", LockException::new),
                new RepositoryExceptionFactoryCase("LoginException", LoginException::new),
                new RepositoryExceptionFactoryCase("MergeException", MergeException::new),
                new RepositoryExceptionFactoryCase("NamespaceException", NamespaceException::new),
                new RepositoryExceptionFactoryCase("ConstraintViolationException", ConstraintViolationException::new),
                new RepositoryExceptionFactoryCase("NoSuchNodeTypeException", NoSuchNodeTypeException::new),
                new RepositoryExceptionFactoryCase("NoSuchWorkspaceException", NoSuchWorkspaceException::new),
                new RepositoryExceptionFactoryCase("PathNotFoundException", PathNotFoundException::new),
                new RepositoryExceptionFactoryCase("InvalidQueryException", InvalidQueryException::new),
                new RepositoryExceptionFactoryCase("ReferentialIntegrityException", ReferentialIntegrityException::new),
                new RepositoryExceptionFactoryCase("RepositoryException", RepositoryException::new),
                new RepositoryExceptionFactoryCase("UnsupportedRepositoryOperationException",
                        UnsupportedRepositoryOperationException::new),
                new RepositoryExceptionFactoryCase("ValueFormatException", ValueFormatException::new),
                new RepositoryExceptionFactoryCase("VersionException", VersionException::new)
        };

        for (RepositoryExceptionFactoryCase factoryCase : cases) {
            RuntimeException rootCause = new RuntimeException(factoryCase.name() + " root");
            RepositoryException exception = factoryCase.factory().create(factoryCase.name() + " message", rootCause);

            assertThat(exception)
                    .isInstanceOf(RepositoryException.class)
                    .hasMessage(factoryCase.name() + " message: " + factoryCase.name() + " root");
            assertThat(exception.getCause()).isSameAs(rootCause);
        }
    }

    @Test
    void specificationConstantsExposeRepositoryQueryObservationAndImportContracts() {
        assertThat(Repository.SPEC_VERSION_DESC).isEqualTo("jcr.specification.version");
        assertThat(Repository.SPEC_NAME_DESC).isEqualTo("jcr.specification.name");
        assertThat(Repository.REP_VENDOR_DESC).isEqualTo("jcr.repository.vendor");
        assertThat(Repository.REP_VENDOR_URL_DESC).isEqualTo("jcr.repository.vendor.url");
        assertThat(Repository.REP_NAME_DESC).isEqualTo("jcr.repository.name");
        assertThat(Repository.REP_VERSION_DESC).isEqualTo("jcr.repository.version");
        assertThat(Repository.LEVEL_1_SUPPORTED).isEqualTo("level.1.supported");
        assertThat(Repository.LEVEL_2_SUPPORTED).isEqualTo("level.2.supported");
        assertThat(Repository.OPTION_TRANSACTIONS_SUPPORTED).isEqualTo("option.transactions.supported");
        assertThat(Repository.OPTION_VERSIONING_SUPPORTED).isEqualTo("option.versioning.supported");
        assertThat(Repository.OPTION_OBSERVATION_SUPPORTED).isEqualTo("option.observation.supported");
        assertThat(Repository.OPTION_LOCKING_SUPPORTED).isEqualTo("option.locking.supported");
        assertThat(Repository.OPTION_QUERY_SQL_SUPPORTED).isEqualTo("option.query.sql.supported");
        assertThat(Repository.QUERY_XPATH_POS_INDEX).isEqualTo("query.xpath.pos.index");
        assertThat(Repository.QUERY_XPATH_DOC_ORDER).isEqualTo("query.xpath.doc.order");

        assertThat(Query.XPATH).isEqualTo("xpath");
        assertThat(Query.SQL).isEqualTo("sql");

        assertThat(Event.NODE_ADDED).isEqualTo(1);
        assertThat(Event.NODE_REMOVED).isEqualTo(2);
        assertThat(Event.PROPERTY_ADDED).isEqualTo(4);
        assertThat(Event.PROPERTY_REMOVED).isEqualTo(8);
        assertThat(Event.PROPERTY_CHANGED).isEqualTo(16);
        assertThat(Event.NODE_ADDED | Event.NODE_REMOVED | Event.PROPERTY_ADDED
                | Event.PROPERTY_REMOVED | Event.PROPERTY_CHANGED).isEqualTo(31);

        assertThat(ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW).isEqualTo(0);
        assertThat(ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING).isEqualTo(1);
        assertThat(ImportUUIDBehavior.IMPORT_UUID_COLLISION_REPLACE_EXISTING).isEqualTo(2);
        assertThat(ImportUUIDBehavior.IMPORT_UUID_COLLISION_THROW).isEqualTo(3);
    }

    private record PropertyTypeCase(int value, String name) {
    }

    private record OnParentVersionActionCase(int value, String name) {
    }

    private record RepositoryExceptionFactoryCase(String name, RepositoryExceptionFactory factory) {
    }

    private interface RepositoryExceptionFactory {
        RepositoryException create(String message, Throwable rootCause);
    }
}
