/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jcr.jcr;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.NoSuchElementException;

import javax.jcr.AccessDeniedException;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Item;
import javax.jcr.ItemVisitor;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.LoginException;
import javax.jcr.MergeException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.Lock;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.observation.Event;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.util.TraversingItemVisitor;
import javax.jcr.version.OnParentVersionAction;
import javax.jcr.version.Version;
import javax.jcr.version.VersionException;
import javax.jcr.version.VersionHistory;
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

    @Test
    void traversingItemVisitorWalksRepositoryItemsBreadthFirst() throws Exception {
        TestNode root = new TestNode("root");
        root.addProperty("title");
        root.addNode(new TestNode("assets").addProperty("mimeType"));
        root.addNode(new TestNode("notes").addProperty("summary"));

        RecordingTraversingItemVisitor visitor = new RecordingTraversingItemVisitor(true, 2);
        root.accept(visitor);

        assertThat(visitor.events()).containsExactly(
                "enter node root @0",
                "leave node root @0",
                "enter property title @1",
                "leave property title @1",
                "enter node assets @1",
                "leave node assets @1",
                "enter node notes @1",
                "leave node notes @1",
                "enter property mimeType @2",
                "leave property mimeType @2",
                "enter property summary @2",
                "leave property summary @2");
    }

    @Test
    void traversingItemVisitorWalksRepositoryItemsDepthFirstBeforeLeavingParent() throws Exception {
        TestNode root = new TestNode("root");
        root.addProperty("title");
        root.addNode(new TestNode("assets").addProperty("mimeType"));
        root.addNode(new TestNode("notes").addProperty("summary"));

        RecordingTraversingItemVisitor visitor = new RecordingTraversingItemVisitor(false, -1);
        root.accept(visitor);

        assertThat(visitor.events()).containsExactly(
                "enter node root @0",
                "enter property title @1",
                "leave property title @1",
                "enter node assets @1",
                "enter property mimeType @2",
                "leave property mimeType @2",
                "leave node assets @1",
                "enter node notes @1",
                "enter property summary @2",
                "leave property summary @2",
                "leave node notes @1",
                "leave node root @0");
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

    private static final class RecordingTraversingItemVisitor extends TraversingItemVisitor {
        private final List<String> events = new ArrayList<>();

        RecordingTraversingItemVisitor(boolean breadthFirst, int maxLevel) {
            super(breadthFirst, maxLevel);
        }

        List<String> events() {
            return events;
        }

        @Override
        protected void entering(Property property, int level) throws RepositoryException {
            events.add("enter property " + property.getName() + " @" + level);
        }

        @Override
        protected void entering(Node node, int level) throws RepositoryException {
            events.add("enter node " + node.getName() + " @" + level);
        }

        @Override
        protected void leaving(Property property, int level) throws RepositoryException {
            events.add("leave property " + property.getName() + " @" + level);
        }

        @Override
        protected void leaving(Node node, int level) throws RepositoryException {
            events.add("leave node " + node.getName() + " @" + level);
        }
    }

    private abstract static class TestItem implements Item {
        private final String name;

        TestItem(String name) {
            this.name = name;
        }

        @Override
        public String getPath() {
            return "/" + name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Item getAncestor(int depth) {
            throw unsupported();
        }

        @Override
        public Node getParent() {
            throw unsupported();
        }

        @Override
        public int getDepth() {
            return 0;
        }

        @Override
        public Session getSession() {
            throw unsupported();
        }

        @Override
        public boolean isNew() {
            return false;
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public boolean isSame(Item otherItem) {
            return this == otherItem;
        }

        @Override
        public void save() {
            throw unsupported();
        }

        @Override
        public void refresh(boolean keepChanges) {
            throw unsupported();
        }

        @Override
        public void remove() {
            throw unsupported();
        }
    }

    private static final class TestNode extends TestItem implements Node {
        private final List<TestProperty> properties = new ArrayList<>();
        private final List<TestNode> nodes = new ArrayList<>();

        TestNode(String name) {
            super(name);
        }

        TestNode addProperty(String name) {
            properties.add(new TestProperty(name));
            return this;
        }

        TestNode addNode(TestNode node) {
            nodes.add(node);
            return this;
        }

        @Override
        public boolean isNode() {
            return true;
        }

        @Override
        public void accept(ItemVisitor visitor) throws RepositoryException {
            visitor.visit(this);
        }

        @Override
        public NodeIterator getNodes() {
            return new TestNodeIterator(nodes);
        }

        @Override
        public PropertyIterator getProperties() {
            return new TestPropertyIterator(properties);
        }

        @Override
        public boolean hasNodes() {
            return !nodes.isEmpty();
        }

        @Override
        public boolean hasProperties() {
            return !properties.isEmpty();
        }

        @Override
        public Node addNode(String relPath) {
            throw unsupported();
        }

        @Override
        public Node addNode(String relPath, String primaryNodeTypeName) {
            throw unsupported();
        }

        @Override
        public void orderBefore(String srcChildRelPath, String destChildRelPath) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, Value value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, Value value, int type) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, Value[] values) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, Value[] values, int type) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, String[] values) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, String[] values, int type) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, String value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, String value, int type) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, InputStream value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, boolean value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, double value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, long value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, Calendar value) {
            throw unsupported();
        }

        @Override
        public Property setProperty(String name, Node value) {
            throw unsupported();
        }

        @Override
        public Node getNode(String relPath) {
            throw unsupported();
        }

        @Override
        public NodeIterator getNodes(String namePattern) {
            throw unsupported();
        }

        @Override
        public Property getProperty(String relPath) {
            throw unsupported();
        }

        @Override
        public PropertyIterator getProperties(String namePattern) {
            throw unsupported();
        }

        @Override
        public Item getPrimaryItem() {
            throw unsupported();
        }

        @Override
        public String getUUID() {
            throw unsupported();
        }

        @Override
        public int getIndex() {
            throw unsupported();
        }

        @Override
        public PropertyIterator getReferences() {
            throw unsupported();
        }

        @Override
        public boolean hasNode(String relPath) {
            throw unsupported();
        }

        @Override
        public boolean hasProperty(String relPath) {
            throw unsupported();
        }

        @Override
        public NodeType getPrimaryNodeType() {
            throw unsupported();
        }

        @Override
        public NodeType[] getMixinNodeTypes() {
            throw unsupported();
        }

        @Override
        public boolean isNodeType(String nodeTypeName) {
            throw unsupported();
        }

        @Override
        public void addMixin(String mixinName) {
            throw unsupported();
        }

        @Override
        public void removeMixin(String mixinName) {
            throw unsupported();
        }

        @Override
        public boolean canAddMixin(String mixinName) {
            throw unsupported();
        }

        @Override
        public NodeDefinition getDefinition() {
            throw unsupported();
        }

        @Override
        public Version checkin() {
            throw unsupported();
        }

        @Override
        public void checkout() {
            throw unsupported();
        }

        @Override
        public void doneMerge(Version version) {
            throw unsupported();
        }

        @Override
        public void cancelMerge(Version version) {
            throw unsupported();
        }

        @Override
        public void update(String srcWorkspace) {
            throw unsupported();
        }

        @Override
        public NodeIterator merge(String srcWorkspace, boolean bestEffort) {
            throw unsupported();
        }

        @Override
        public String getCorrespondingNodePath(String workspaceName) {
            throw unsupported();
        }

        @Override
        public boolean isCheckedOut() {
            throw unsupported();
        }

        @Override
        public void restore(String versionName, boolean removeExisting) {
            throw unsupported();
        }

        @Override
        public void restore(Version version, boolean removeExisting) {
            throw unsupported();
        }

        @Override
        public void restore(Version version, String relPath, boolean removeExisting) {
            throw unsupported();
        }

        @Override
        public void restoreByLabel(String versionLabel, boolean removeExisting) {
            throw unsupported();
        }

        @Override
        public VersionHistory getVersionHistory() {
            throw unsupported();
        }

        @Override
        public Version getBaseVersion() {
            throw unsupported();
        }

        @Override
        public Lock lock(boolean isDeep, boolean isSessionScoped) {
            throw unsupported();
        }

        @Override
        public Lock getLock() {
            throw unsupported();
        }

        @Override
        public void unlock() {
            throw unsupported();
        }

        @Override
        public boolean holdsLock() {
            throw unsupported();
        }

        @Override
        public boolean isLocked() {
            throw unsupported();
        }
    }

    private static final class TestProperty extends TestItem implements Property {
        TestProperty(String name) {
            super(name);
        }

        @Override
        public boolean isNode() {
            return false;
        }

        @Override
        public void accept(ItemVisitor visitor) throws RepositoryException {
            visitor.visit(this);
        }

        @Override
        public void setValue(Value value) {
            throw unsupported();
        }

        @Override
        public void setValue(Value[] values) {
            throw unsupported();
        }

        @Override
        public void setValue(String value) {
            throw unsupported();
        }

        @Override
        public void setValue(String[] values) {
            throw unsupported();
        }

        @Override
        public void setValue(InputStream value) {
            throw unsupported();
        }

        @Override
        public void setValue(long value) {
            throw unsupported();
        }

        @Override
        public void setValue(double value) {
            throw unsupported();
        }

        @Override
        public void setValue(Calendar value) {
            throw unsupported();
        }

        @Override
        public void setValue(boolean value) {
            throw unsupported();
        }

        @Override
        public void setValue(Node value) {
            throw unsupported();
        }

        @Override
        public Value getValue() {
            throw unsupported();
        }

        @Override
        public Value[] getValues() {
            throw unsupported();
        }

        @Override
        public String getString() {
            throw unsupported();
        }

        @Override
        public InputStream getStream() {
            throw unsupported();
        }

        @Override
        public long getLong() {
            throw unsupported();
        }

        @Override
        public double getDouble() {
            throw unsupported();
        }

        @Override
        public Calendar getDate() {
            throw unsupported();
        }

        @Override
        public boolean getBoolean() {
            throw unsupported();
        }

        @Override
        public Node getNode() {
            throw unsupported();
        }

        @Override
        public long getLength() {
            throw unsupported();
        }

        @Override
        public long[] getLengths() {
            throw unsupported();
        }

        @Override
        public PropertyDefinition getDefinition() {
            throw unsupported();
        }

        @Override
        public int getType() {
            throw unsupported();
        }
    }

    private abstract static class TestRangeIterator<T> {
        private final List<T> items;
        private int position;

        TestRangeIterator(List<? extends T> items) {
            this.items = new ArrayList<>(items);
        }

        public boolean hasNext() {
            return position < items.size();
        }

        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return items.get(position++);
        }

        public void skip(long skipNum) {
            if (skipNum < 0 || skipNum > items.size() - position) {
                throw new NoSuchElementException();
            }
            position += (int) skipNum;
        }

        public long getSize() {
            return items.size();
        }

        public long getPosition() {
            return position;
        }
    }

    private static final class TestNodeIterator extends TestRangeIterator<Node> implements NodeIterator {
        TestNodeIterator(List<? extends Node> nodes) {
            super(nodes);
        }

        @Override
        public Node nextNode() {
            return (Node) next();
        }
    }

    private static final class TestPropertyIterator extends TestRangeIterator<Property> implements PropertyIterator {
        TestPropertyIterator(List<? extends Property> properties) {
            super(properties);
        }

        @Override
        public Property nextProperty() {
            return (Property) next();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not needed by this test");
    }
}
