/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.IMAPResponse;
import com.sun.mail.imap.protocol.ListInfo;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import org.junit.jupiter.api.Test;

public class IMAPStoreTest {
    private static final String MISSING_FOLDER_CLASS = "org_eclipse_angus.imap.DoesNotExist";

    @Test
    void customFolderClassIsLoadedAndUsedForFolderCreation() throws Exception {
        Session session = sessionWithFolderClass(CustomFolder.class.getName());
        TestIMAPStore store = new TestIMAPStore(session);

        Folder namedFolder = store.createFolder("INBOX", '/', Boolean.FALSE);
        Folder listedFolder = store.createFolder(listInfo(
            "* LIST (\\HasNoChildren) \"/\" \"Archive\""));

        assertThat(namedFolder)
            .isInstanceOf(CustomFolder.class)
            .extracting(Folder::getFullName)
            .isEqualTo("INBOX");
        assertThat(listedFolder)
            .isInstanceOf(CustomFolder.class)
            .extracting(Folder::getFullName)
            .isEqualTo("Archive");
    }

    @Test
    void missingCustomFolderClassFallsBackToDefaultFolderImplementation() {
        TestIMAPStore store = new TestIMAPStore(sessionWithFolderClass(MISSING_FOLDER_CLASS));

        Folder folder = store.createFolder("INBOX", '/', null);

        assertThat(folder)
            .isNotInstanceOf(CustomFolder.class)
            .isInstanceOf(IMAPFolder.class)
            .extracting(Folder::getFullName)
            .isEqualTo("INBOX");
    }

    private static Session sessionWithFolderClass(String folderClassName) {
        Properties properties = new Properties();
        properties.setProperty("mail.imap.folder.class", folderClassName);
        return Session.getInstance(properties);
    }

    private static ListInfo listInfo(String response) throws IOException, ProtocolException {
        return new ListInfo(new IMAPResponse(response));
    }

    public static class TestIMAPStore extends IMAPStore {
        TestIMAPStore(Session session) {
            super(session, new URLName("imap://localhost"), "imap", false);
        }

        IMAPFolder createFolder(String fullName, char separator, Boolean isNamespace) {
            return newIMAPFolder(fullName, separator, isNamespace);
        }

        IMAPFolder createFolder(ListInfo listInfo) {
            return newIMAPFolder(listInfo);
        }
    }

    public static class CustomFolder extends IMAPFolder {
        public CustomFolder(String fullName, char separator, IMAPStore store, Boolean isNamespace) {
            super(fullName, separator, store, isNamespace);
        }

        public CustomFolder(ListInfo listInfo, IMAPStore store) {
            super(listInfo, store);
        }
    }
}
