/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_api.api_ldap_model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.filter.AndNode;
import org.apache.directory.api.ldap.model.filter.EqualityNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.NotNode;
import org.apache.directory.api.ldap.model.filter.OrNode;
import org.apache.directory.api.ldap.model.filter.PresenceNode;
import org.apache.directory.api.ldap.model.filter.SubstringNode;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.ldif.LdifUtils;
import org.apache.directory.api.ldap.model.message.AddRequestImpl;
import org.apache.directory.api.ldap.model.message.AliasDerefMode;
import org.apache.directory.api.ldap.model.message.BindRequest;
import org.apache.directory.api.ldap.model.message.BindRequestImpl;
import org.apache.directory.api.ldap.model.message.CompareRequest;
import org.apache.directory.api.ldap.model.message.CompareRequestImpl;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.message.DeleteRequest;
import org.apache.directory.api.ldap.model.message.DeleteRequestImpl;
import org.apache.directory.api.ldap.model.message.LdapResultImpl;
import org.apache.directory.api.ldap.model.message.MessageTypeEnum;
import org.apache.directory.api.ldap.model.message.ModifyDnRequest;
import org.apache.directory.api.ldap.model.message.ModifyDnRequestImpl;
import org.apache.directory.api.ldap.model.message.ModifyRequest;
import org.apache.directory.api.ldap.model.message.ModifyRequestImpl;
import org.apache.directory.api.ldap.model.message.ReferralImpl;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.message.controls.ChangeType;
import org.apache.directory.api.ldap.model.message.controls.EntryChangeImpl;
import org.apache.directory.api.ldap.model.message.controls.ManageDsaITImpl;
import org.apache.directory.api.ldap.model.message.controls.OpaqueControl;
import org.apache.directory.api.ldap.model.message.controls.PagedResultsImpl;
import org.apache.directory.api.ldap.model.message.controls.PersistentSearchImpl;
import org.apache.directory.api.ldap.model.message.controls.SortKey;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.url.LdapUrl;
import org.junit.jupiter.api.Test;

public class Api_ldap_modelTest {
    @Test
    void namesModelEscapingAndHierarchy() throws Exception {
        Dn base = new Dn("dc=example,dc=com");
        Dn people = new Dn("ou=People", "dc=example", "dc=com");
        Dn person = new Dn("cn=John Doe,ou=People,dc=example,dc=com");
        Rdn multiValuedRdn = new Rdn("cn=John Doe+sn=Doe");

        assertThat(base.isAncestorOf(person)).isTrue();
        assertThat(person.isDescendantOf(base)).isTrue();
        assertThat(person.getParent()).isEqualTo(people);
        assertThat(person.getRdn().getType()).isEqualTo("cn");
        assertThat(person.getRdn().getValue()).isEqualTo("John Doe");
        assertThat(multiValuedRdn.size()).isEqualTo(2);
        assertThat(multiValuedRdn.getAva("sn")).isNotNull();
        assertThat(Dn.isValid(person.getName())).isTrue();
        assertThat(Rdn.escapeValue(" leading,plus+hash# ")).contains("\\,", "\\+");
        assertThat(Rdn.unescapeValue("John\\, Doe")).isEqualTo("John, Doe");
        assertThat(person.getName().getBytes(StandardCharsets.UTF_8))
                .containsSequence("cn=John Doe".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void entriesAttributesValuesAndModificationsMaintainDirectoryData() throws Exception {
        Dn dn = new Dn("cn=John Doe,ou=People,dc=example,dc=com");
        Entry entry = new DefaultEntry(dn);
        Attribute objectClass = new DefaultAttribute("objectClass", "top", "person", "inetOrgPerson");
        Attribute cn = new DefaultAttribute("cn", new Value("John Doe"));
        Attribute photo = new DefaultAttribute("jpegPhoto", new byte[] {1, 2, 3, 4});

        entry.add(objectClass, cn, photo);
        entry.add("sn", "Doe");
        entry.put("mail", "john.doe@example.com", "jdoe@example.com");

        assertThat(entry.getDn()).isEqualTo(dn);
        assertThat(entry.size()).isEqualTo(5);
        assertThat(entry.containsAttribute("objectClass", "cn", "sn", "mail", "jpegPhoto")).isTrue();
        assertThat(entry.hasObjectClass("person", "inetOrgPerson")).isTrue();
        assertThat(entry.contains("mail", "john.doe@example.com", "jdoe@example.com")).isTrue();
        assertThat(entry.get("cn").getString()).isEqualTo("John Doe");
        assertThat(entry.get("jpegPhoto").getBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);

        Entry cloned = entry.clone();
        cloned.remove("mail", "jdoe@example.com");
        cloned.removeAttributes("jpegPhoto");

        assertThat(cloned).isNotSameAs(entry);
        assertThat(cloned.contains("mail", "jdoe@example.com")).isFalse();
        assertThat(cloned.containsAttribute("jpegPhoto")).isFalse();
        assertThat(entry.contains("mail", "jdoe@example.com")).isTrue();
        assertThat(entry.containsAttribute("jpegPhoto")).isTrue();

        Modification addTelephone = new DefaultModification(
                ModificationOperation.ADD_ATTRIBUTE,
                "telephoneNumber",
                "+1 555 0100");
        Modification replaceMail = new DefaultModification(
                ModificationOperation.REPLACE_ATTRIBUTE,
                new DefaultAttribute("mail", "john@example.com"));

        assertThat(addTelephone.getOperation()).isEqualTo(ModificationOperation.ADD_ATTRIBUTE);
        assertThat(addTelephone.getAttribute().contains("+1 555 0100")).isTrue();
        assertThat(replaceMail.getOperation().getValue()).isEqualTo(ModificationOperation.REPLACE_ATTRIBUTE.getValue());
        assertThat(ModificationOperation.getOperation(addTelephone.getOperation().getValue()))
                .isEqualTo(ModificationOperation.ADD_ATTRIBUTE);
    }

    @Test
    void searchRequestsAcceptParsedAndProgrammaticFilters() throws Exception {
        EqualityNode<String> mailEquals = new EqualityNode<>("mail", "john@example.com");
        PresenceNode hasCn = new PresenceNode("cn");
        SubstringNode surnameStartsWithDo = new SubstringNode("sn", "Do", null);
        OrNode alternatives = new OrNode(mailEquals, surnameStartsWithDo);
        AndNode filter = new AndNode(hasCn, alternatives);
        NotNode negated = new NotNode(new PresenceNode("description"));
        filter.addNode(negated);

        SearchRequest search = new SearchRequestImpl()
                .setMessageId(10)
                .setBase(new Dn("ou=People,dc=example,dc=com"))
                .setScope(SearchScope.SUBTREE)
                .setDerefAliases(AliasDerefMode.DEREF_IN_SEARCHING)
                .setSizeLimit(25)
                .setTimeLimit(30)
                .setTypesOnly(false)
                .setFilter(filter)
                .addAttributes("cn", "sn", "mail");

        assertThat(search.getType()).isEqualTo(MessageTypeEnum.SEARCH_REQUEST);
        assertThat(search.getResponseTypes()).contains(
                MessageTypeEnum.SEARCH_RESULT_ENTRY,
                MessageTypeEnum.SEARCH_RESULT_REFERENCE,
                MessageTypeEnum.SEARCH_RESULT_DONE);
        assertThat(search.getAttributes()).containsExactly("cn", "sn", "mail");
        assertThat(search.getDerefAliases().isDerefInSearching()).isTrue();
        assertThat(((AndNode) search.getFilter()).getChildren()).hasSize(3);
        assertThat(search.removeAttribute("sn").getAttributes()).containsExactly("cn", "mail");

        SearchRequest parsedSearch = new SearchRequestImpl()
                .setBase(new Dn("dc=example,dc=com"))
                .setFilter("(&(objectClass=person)(|(cn=John Doe)(mail=john@example.com)))");
        ExprNode parsedFilter = parsedSearch.getFilter();

        assertThat(parsedFilter).isInstanceOf(AndNode.class);
        assertThat(((AndNode) parsedFilter).getChildren()).hasSize(2);
        assertThat(parsedSearch.getResultResponse().getType()).isEqualTo(MessageTypeEnum.SEARCH_RESULT_DONE);
    }

    @Test
    void requestMessagesExposeTypedStateControlsAndResponses() throws Exception {
        Control manageDsaIt = new ManageDsaITImpl(true);
        PagedResultsImpl pagedResults = new PagedResultsImpl();
        pagedResults.setCritical(true);
        pagedResults.setSize(100);
        pagedResults.setCookie(new byte[] {9, 8, 7});

        BindRequest bind = new BindRequestImpl()
                .setMessageId(1)
                .setDn(new Dn("uid=john,ou=People,dc=example,dc=com"))
                .setSimple(true)
                .setCredentials("secret")
                .addControl(manageDsaIt)
                .addControl(pagedResults);
        AddRequestImpl add = new AddRequestImpl();
        add.setMessageId(2);
        add.setEntry(new DefaultEntry("cn=Jane Doe,ou=People,dc=example,dc=com"));
        add.addAttributeType("objectClass");
        add.addAttributeValue("top");
        add.addAttributeValue("person");
        add.addAttributeType("cn");
        add.addAttributeValue("Jane Doe");

        CompareRequest compare = new CompareRequestImpl()
                .setMessageId(3)
                .setName(new Dn("cn=Jane Doe,ou=People,dc=example,dc=com"))
                .setAttributeId("cn")
                .setAssertionValue("Jane Doe");
        DeleteRequest delete = new DeleteRequestImpl()
                .setMessageId(4)
                .setName(new Dn("cn=Obsolete,ou=People,dc=example,dc=com"));
        ModifyDnRequest rename = new ModifyDnRequestImpl()
                .setMessageId(5)
                .setName(new Dn("cn=Jane Doe,ou=People,dc=example,dc=com"))
                .setNewRdn(new Rdn("cn=Jane Smith"))
                .setNewSuperior(new Dn("ou=Alumni,dc=example,dc=com"))
                .setDeleteOldRdn(true);

        assertThat(bind.getType()).isEqualTo(MessageTypeEnum.BIND_REQUEST);
        assertThat(bind.getResultResponse().getType()).isEqualTo(MessageTypeEnum.BIND_RESPONSE);
        assertThat(bind.getCredentials()).containsExactly("secret".getBytes(StandardCharsets.UTF_8));
        assertThat(bind.getControls()).containsKeys(manageDsaIt.getOid(), pagedResults.getOid());
        assertThat(bind.getControl(pagedResults.getOid()).isCritical()).isTrue();
        assertThat(pagedResults.getSize()).isEqualTo(100);
        assertThat(pagedResults.getCookie()).containsExactly((byte) 9, (byte) 8, (byte) 7);

        assertThat(add.getEntry().get("objectClass").contains("top", "person")).isTrue();
        assertThat(add.getEntry().get("cn").contains("Jane Doe")).isTrue();
        assertThat(add.getResponseType()).isEqualTo(MessageTypeEnum.ADD_RESPONSE);
        assertThat(compare.getAssertionValue().getString()).isEqualTo("Jane Doe");
        assertThat(compare.getResultResponse().getType()).isEqualTo(MessageTypeEnum.COMPARE_RESPONSE);
        assertThat(delete.getName().getName()).isEqualTo("cn=Obsolete,ou=People,dc=example,dc=com");
        assertThat(delete.getResponseType()).isEqualTo(MessageTypeEnum.DEL_RESPONSE);
        assertThat(rename.getNewRdn().getValue()).isEqualTo("Jane Smith");
        assertThat(rename.getNewSuperior().getName()).isEqualTo("ou=Alumni,dc=example,dc=com");
        assertThat(rename.isMove()).isTrue();
        assertThat(rename.getDeleteOldRdn()).isTrue();
    }

    @Test
    void ldifReaderParsesContentAndChangeRecords() throws Exception {
        String contentLdif = """
                version: 1

                dn: cn=Jane Doe,ou=People,dc=example,dc=com
                objectClass: top
                objectClass: person
                cn: Jane Doe
                sn: Doe
                jpegPhoto:: AQIDBA==
                """;

        List<LdifEntry> contentEntries = new ArrayList<>();
        try (LdifReader reader = new LdifReader(new StringReader(contentLdif))) {
            assertThat(reader.getVersion()).isEqualTo(1);
            assertThat(reader.containsEntries()).isTrue();
            for (LdifEntry entry : reader) {
                contentEntries.add(entry);
            }
            assertThat(reader.hasError()).isFalse();
        }

        assertThat(contentEntries).hasSize(1);
        LdifEntry contentEntry = contentEntries.get(0);
        assertThat(contentEntry.isEntry()).isTrue();
        assertThat(contentEntry.isLdifContent()).isTrue();
        assertThat(contentEntry.getDn().getName()).isEqualTo("cn=Jane Doe,ou=People,dc=example,dc=com");
        assertThat(contentEntry.get("objectClass").contains("top", "person")).isTrue();
        assertThat(contentEntry.get("jpegPhoto").getBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);

        String renderedLdif = LdifUtils.convertToLdif(contentEntry.getEntry());
        assertThat(renderedLdif).contains(
                "dn: cn=Jane Doe,ou=People,dc=example,dc=com",
                "objectClass: top",
                "objectClass: person",
                "cn: Jane Doe");

        String changeLdif = """
                version: 1

                dn: cn=Jane Doe,ou=People,dc=example,dc=com
                changetype: modify
                replace: mail
                mail: jane@example.com
                -
                add: telephoneNumber
                telephoneNumber: +1 555 0110
                -
                """;
        List<LdifEntry> changeEntries = new ArrayList<>();
        try (LdifReader reader = new LdifReader(new StringReader(changeLdif))) {
            assertThat(reader.getVersion()).isEqualTo(1);
            for (LdifEntry entry : reader) {
                changeEntries.add(entry);
            }
            assertThat(reader.hasError()).isFalse();
        }

        assertThat(changeEntries).hasSize(1);
        LdifEntry changeEntry = changeEntries.get(0);
        assertThat(changeEntry.isLdifChange()).isTrue();
        assertThat(changeEntry.isChangeModify()).isTrue();
        assertThat(changeEntry.getModifications()).hasSize(2);
        assertThat(changeEntry.getModifications())
                .extracting(Modification::getOperation)
                .containsExactly(ModificationOperation.REPLACE_ATTRIBUTE, ModificationOperation.ADD_ATTRIBUTE);
        assertThat(changeEntry.getModifications())
                .extracting(modification -> modification.getAttribute().getUpId())
                .containsExactly("mail", "telephoneNumber");
        assertThat(changeEntry.getModifications().get(0).getAttribute().contains("jane@example.com")).isTrue();
        assertThat(changeEntry.getModifications().get(1).getAttribute().contains("+1 555 0110")).isTrue();
    }

    @Test
    void ldapUrlsParseDecodeAndRenderSearchLocations() throws Exception {
        LdapUrl parsedUrl = new LdapUrl(
                "ldaps://ldap.example.com:1636/ou=People,dc=example,dc=com?cn,sn,mail?sub?"
                        + "(|(cn=John%20Doe)(mail=john@example.com))?!x-priority=high%2cmedium,x-empty");

        assertThat(parsedUrl.getScheme()).isEqualTo(LdapUrl.LDAPS_SCHEME);
        assertThat(parsedUrl.getHost()).isEqualTo("ldap.example.com");
        assertThat(parsedUrl.getPort()).isEqualTo(1636);
        assertThat(parsedUrl.getDn()).isEqualTo(new Dn("ou=People,dc=example,dc=com"));
        assertThat(parsedUrl.getAttributes()).containsExactly("cn", "sn", "mail");
        assertThat(parsedUrl.getScope()).isEqualTo(SearchScope.SUBTREE);
        assertThat(parsedUrl.getFilter()).isEqualTo("(|(cn=John Doe)(mail=john@example.com))");
        assertThat(parsedUrl.getExtensions()).hasSize(2);
        assertThat(parsedUrl.getExtension("X-PRIORITY").isCritical()).isTrue();
        assertThat(parsedUrl.getExtensionValue("x-priority")).isEqualTo("high,medium");
        assertThat(parsedUrl.getExtension("x-empty").getValue()).isNull();
        assertThat(parsedUrl.getBytesCopy()).containsExactly(
                parsedUrl.getString().getBytes(StandardCharsets.UTF_8));
        assertThat(parsedUrl.toString()).isEqualTo(
                "ldaps://ldap.example.com:1636/ou=People,dc=example,dc=com?cn,sn,mail?sub?"
                        + "(%7C(cn=John%20Doe)(mail=john@example.com))?!x-priority=high%2cmedium,x-empty");

        LdapUrl builtUrl = new LdapUrl();
        builtUrl.setScheme(LdapUrl.LDAP_SCHEME);
        builtUrl.setHost("directory.example.org");
        builtUrl.setPort(389);
        builtUrl.setDn(new Dn("cn=Jane Smith,ou=People,dc=example,dc=com"));
        builtUrl.setAttributes(Arrays.asList("cn", "mail"));
        builtUrl.setScope(SearchScope.ONELEVEL);
        builtUrl.setFilter("(mail=jane.smith@example.com)");
        builtUrl.getExtensions().add(new LdapUrl.Extension(false, "x-note", "primary,secondary"));

        assertThat(builtUrl.toString()).isEqualTo(
                "ldap://directory.example.org:389/cn=Jane%20Smith,ou=People,dc=example,dc=com?cn,mail?one?"
                        + "(mail=jane.smith@example.com)?x-note=primary%2csecondary");
        assertThat(new LdapUrl(builtUrl.toString())).isEqualTo(builtUrl);
    }

    @Test
    void modifyRequestsResultsReferralsControlsAndCursorsCoverModelUtilities() throws Exception {
        ModifyRequest modify = new ModifyRequestImpl()
                .setMessageId(20)
                .setName(new Dn("cn=John Doe,ou=People,dc=example,dc=com"))
                .add("telephoneNumber", "+1 555 0100")
                .replace("mail", "john@example.com")
                .remove(new DefaultAttribute("description"));

        Collection<Modification> modifications = modify.getModifications();
        List<ModificationOperation> operations = new ArrayList<>();
        List<String> attributeIds = new ArrayList<>();
        for (Modification modification : modifications) {
            operations.add(modification.getOperation());
            attributeIds.add(modification.getAttribute().getUpId());
        }

        assertThat(modify.getResponseType()).isEqualTo(MessageTypeEnum.MODIFY_RESPONSE);
        assertThat(modify.getResultResponse().getType()).isEqualTo(MessageTypeEnum.MODIFY_RESPONSE);
        assertThat(operations).containsExactly(
                ModificationOperation.ADD_ATTRIBUTE,
                ModificationOperation.REPLACE_ATTRIBUTE,
                ModificationOperation.REMOVE_ATTRIBUTE);
        assertThat(attributeIds).containsExactly("telephoneNumber", "mail", "description");

        ReferralImpl referral = new ReferralImpl();
        referral.addLdapUrl("ldap://ldap.example.com/ou=People,dc=example,dc=com");
        referral.addLdapUrlBytes("ldap://backup.example.com/dc=example,dc=com".getBytes(StandardCharsets.UTF_8));
        LdapResultImpl result = new LdapResultImpl();
        result.setResultCode(ResultCodeEnum.REFERRAL);
        result.setMatchedDn(new Dn("dc=example,dc=com"));
        result.setDiagnosticMessage("Use referred directory");
        result.setReferral(referral);

        assertThat(result.isReferral()).isTrue();
        assertThat(result.isDefaultSuccess()).isFalse();
        assertThat(result.getReferral().getLdapUrls()).contains("ldap://ldap.example.com/ou=People,dc=example,dc=com");
        Collection<byte[]> ldapUrlBytes = result.getReferral().getLdapUrlsBytes();
        assertThat(ldapUrlBytes).hasSize(1);
        assertThat(new String(ldapUrlBytes.iterator().next(), StandardCharsets.UTF_8))
                .isEqualTo("ldap://backup.example.com/dc=example,dc=com");
        assertThat(ResultCodeEnum.getResultCode(ResultCodeEnum.REFERRAL.getValue())).isEqualTo(ResultCodeEnum.REFERRAL);

        EntryChangeImpl entryChange = new EntryChangeImpl();
        entryChange.setChangeType(ChangeType.MODDN);
        entryChange.setPreviousDn(new Dn("cn=John Doe,ou=People,dc=example,dc=com"));
        entryChange.setChangeNumber(42L);
        PersistentSearchImpl persistentSearch = new PersistentSearchImpl();
        persistentSearch.setChangeTypes(0);
        persistentSearch.enableNotification(ChangeType.ADD);
        persistentSearch.enableNotification(ChangeType.MODIFY);
        persistentSearch.setChangesOnly(true);
        persistentSearch.setReturnECs(true);
        OpaqueControl opaqueControl = new OpaqueControl("1.2.3.4", true);
        opaqueControl.setEncodedValue(new byte[] {1, 3, 3, 7});
        SortKey sortKey = new SortKey("sn", "caseIgnoreOrderingMatch", true);

        assertThat(entryChange.getChangeType()).isEqualTo(ChangeType.MODDN);
        assertThat(entryChange.getPreviousDn().getName()).isEqualTo("cn=John Doe,ou=People,dc=example,dc=com");
        assertThat(entryChange.getChangeNumber()).isEqualTo(42L);
        assertThat(persistentSearch.isNotificationEnabled(ChangeType.ADD)).isTrue();
        assertThat(persistentSearch.isNotificationEnabled(ChangeType.DELETE)).isFalse();
        assertThat(persistentSearch.isChangesOnly()).isTrue();
        assertThat(persistentSearch.isReturnECs()).isTrue();
        assertThat(opaqueControl.hasEncodedValue()).isTrue();
        assertThat(opaqueControl.getEncodedValue()).containsExactly((byte) 1, (byte) 3, (byte) 3, (byte) 7);
        assertThat(sortKey.getAttributeTypeDesc()).isEqualTo("sn");
        assertThat(sortKey.getMatchingRuleId()).isEqualTo("caseIgnoreOrderingMatch");
        assertThat(sortKey.isReverseOrder()).isTrue();

        Map<String, Control> controlsByOid = Map.of(
                opaqueControl.getOid(), opaqueControl,
                entryChange.getOid(), entryChange);
        assertThat(controlsByOid).containsEntry(opaqueControl.getOid(), opaqueControl);

        List<String> cursorValues = readCursor(new ListCursor<>(Arrays.asList("alpha", "bravo", "charlie")));
        assertThat(cursorValues).containsExactly("alpha", "bravo", "charlie");
    }

    private static List<String> readCursor(ListCursor<String> cursor) throws Exception {
        List<String> values = new ArrayList<>();
        try {
            cursor.beforeFirst();
            while (cursor.next()) {
                values.add(cursor.get());
            }
            assertThat(cursor.isAfterLast()).isTrue();
        } finally {
            cursor.close();
        }
        return values;
    }
}
