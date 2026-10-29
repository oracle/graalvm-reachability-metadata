/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_roo.org_springframework_roo_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.roo.addon.javabean.RooJavaBean;
import org.springframework.roo.addon.jpa.activerecord.RooJpaActiveRecord;
import org.springframework.roo.addon.jpa.entity.RooJpaEntity;
import org.springframework.roo.addon.json.RooJson;
import org.springframework.roo.addon.layers.service.RooService;
import org.springframework.roo.addon.web.mvc.controller.json.RooWebJson;
import org.springframework.roo.classpath.operations.jsr303.RooUploadedFile;

/// Roo annotations are source-retained; code generation belongs to separate Roo add-ons.
/// The fixtures exercise annotation syntax, while assertions check the exported naming contract.
public class Org_springframework_roo_annotationsTest {
    @Test
    void entityFieldNamesFollowThePublicPersistenceContract() {
        assertThat(RooJpaEntity.ID_FIELD_DEFAULT).isEqualTo("id");
        assertThat(RooJpaEntity.VERSION_FIELD_DEFAULT).isEqualTo("version");
        assertThat(RooJpaEntity.VERSION_COLUMN_DEFAULT).isEqualTo("version");
    }

    @Test
    void activeRecordPrefixesProduceTheDocumentedFinderNames() {
        assertThat(RooJpaActiveRecord.COUNT_METHOD_DEFAULT + "Documents").isEqualTo("countDocuments");
        assertThat(RooJpaActiveRecord.FIND_METHOD_DEFAULT + "Document").isEqualTo("findDocument");
        assertThat(RooJpaActiveRecord.FIND_ALL_METHOD_DEFAULT + "Documents").isEqualTo("findAllDocuments");
        assertThat(RooJpaActiveRecord.FIND_ENTRIES_METHOD_DEFAULT + "DocumentEntries")
                .isEqualTo("findDocumentEntries");
        assertThat(RooJpaActiveRecord.FIND_ALL_SORTED_METHOD_DEFAULT)
                .isEqualTo(RooJpaActiveRecord.FIND_ALL_METHOD_DEFAULT);
        assertThat(RooJpaActiveRecord.FIND_ENTRIES_SORTED_METHOD_DEFAULT)
                .isEqualTo(RooJpaActiveRecord.FIND_ENTRIES_METHOD_DEFAULT);
        assertThat(RooJpaActiveRecord.CLEAR_METHOD_DEFAULT).isEqualTo("clear");
        assertThat(RooJpaActiveRecord.FLUSH_METHOD_DEFAULT).isEqualTo("flush");
        assertThat(RooJpaActiveRecord.MERGE_METHOD_DEFAULT).isEqualTo("merge");
        assertThat(RooJpaActiveRecord.PERSIST_METHOD_DEFAULT).isEqualTo("persist");
        assertThat(RooJpaActiveRecord.REMOVE_METHOD_DEFAULT).isEqualTo("remove");
    }

    @Test
    void serviceNamesDistinguishCollectionAndSingleEntityOperations() {
        assertThat(RooService.DOMAIN_TYPES_ATTRIBUTE).isEqualTo("domainTypes");
        assertThat(RooService.COUNT_ALL_METHOD + "Documents").isEqualTo("countAllDocuments");
        assertThat(RooService.FIND_ALL_METHOD + "Documents").isEqualTo("findAllDocuments");
        assertThat(RooService.FIND_ENTRIES_METHOD + "DocumentEntries").isEqualTo("findDocumentEntries");
        assertThat(RooService.FIND_METHOD + "Document").isEqualTo("findDocument");
        assertThat(RooService.SAVE_METHOD).isEqualTo("save");
        assertThat(RooService.UPDATE_METHOD).isEqualTo("update");
        assertThat(RooService.DELETE_METHOD).isEqualTo("delete");
    }

    @Test
    void jsonControllerNamesDistinguishSingleAndBulkRequests() {
        assertThat(RooWebJson.CREATE_FROM_JSON).isEqualTo("createFromJson");
        assertThat(RooWebJson.CREATE_FROM_JSON_ARRAY).isEqualTo("createFromJsonArray");
        assertThat(RooWebJson.UPDATE_FROM_JSON).isEqualTo("updateFromJson");
        assertThat(RooWebJson.UPDATE_FROM_JSON_ARRAY).isEqualTo("updateFromJsonArray");
        assertThat(RooWebJson.DELETE_FROM_JSON_ARRAY).isEqualTo("deleteFromJson");
        assertThat(RooWebJson.LIST_JSON).isEqualTo("listJson");
        assertThat(RooWebJson.SHOW_JSON).isEqualTo("showJson");
        assertThat(RooWebJson.EXPOSE_FINDERS).isTrue();
    }

    @RooJavaBean
    @RooJpaEntity(table = "documents", identifierType = Long.class, versionType = Integer.class)
    @RooJson(rootName = "document", iso8601Dates = true, deepSerialize = false)
    private static class Document {
        @RooUploadedFile(contentType = "application/pdf", autoUpload = true)
        private byte[] contents;
    }

    @RooJpaActiveRecord(entityName = "ArchivedDocument", finders = {"findArchivedDocumentsByTitleEquals"},
            identifierField = "documentId", versionField = "revision", persistenceUnit = "archive")
    private static class ArchivedDocument {
    }

    @RooService(domainTypes = {Document.class, ArchivedDocument.class}, requireAuthentication = true,
            authorizedReadRoles = {"ROLE_READER"}, authorizedCreateOrUpdateRoles = {"ROLE_EDITOR"},
            authorizedDeleteRoles = {"ROLE_ADMIN"}, usePermissionEvaluator = true)
    private interface DocumentService {
    }

    @RooWebJson(jsonObject = Document.class, exposeFinders = false, createFromJsonArrayMethod = "",
            updateFromJsonArrayMethod = "")
    private static class DocumentController {
    }
}
