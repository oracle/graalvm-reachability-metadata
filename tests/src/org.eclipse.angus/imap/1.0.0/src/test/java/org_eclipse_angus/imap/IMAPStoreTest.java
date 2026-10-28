/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.ListInfo;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class IMAPStoreTest {
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(60)
    public void configuredFolderClassHandlesNamedAndListedMailboxes() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.imap.folder.class", CustomIMAPFolder.class.getName());
        properties.setProperty("mail.imap.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.timeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.writetimeout", Integer.toString(TIMEOUT_MILLIS));
        Session session = Session.getInstance(properties);

        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(TIMEOUT_MILLIS);
            Future<List<String>> conversation = executor.submit(() -> serveFolders(server));

            try (IMAPStore store = new IMAPStore(session, new URLName("imap://localhost"))) {
                store.connect(server.getInetAddress().getHostAddress(), server.getLocalPort(), "reader", "secret");

                Folder named = store.getFolder("Projects/Drafts");
                assertThat(named).isInstanceOf(CustomIMAPFolder.class);
                assertThat(((CustomIMAPFolder) named).construction).isEqualTo("name");
                assertThat(named.getFullName()).isEqualTo("Projects/Drafts");
                assertThat(named.getStore()).isSameAs(store);
                assertThat(named.isOpen()).isFalse();

                Folder fromUrl = store.getFolder(new URLName("imap", "localhost", -1, "INBOX", null, null));
                assertThat(fromUrl).isInstanceOf(CustomIMAPFolder.class);
                assertThat(((CustomIMAPFolder) fromUrl).construction).isEqualTo("name");
                assertThat(fromUrl.getFullName()).isEqualTo("INBOX");

                Folder[] listed = store.getDefaultFolder().list("*");
                assertThat(listed).hasSize(2);
                assertThat(listed).allSatisfy(folder -> {
                    assertThat(folder).isInstanceOf(CustomIMAPFolder.class);
                    assertThat(((CustomIMAPFolder) folder).construction).isEqualTo("listing");
                    assertThat(folder.getStore()).isSameAs(store);
                    assertThat(folder.getSeparator()).isEqualTo('/');
                    assertThat(folder.isOpen()).isFalse();
                });
                assertThat(listed[0].getFullName()).isEqualTo("INBOX");
                assertThat(listed[0].getType()).isEqualTo(Folder.HOLDS_MESSAGES);
                assertThat(listed[1].getFullName()).isEqualTo("Projects");
                assertThat(listed[1].getType()).isEqualTo(Folder.HOLDS_FOLDERS);
            }

            assertThat(conversation.get(30, TimeUnit.SECONDS)).contains("LOGIN", "LIST", "LOGOUT");
        }
    }

    private static List<String> serveFolders(ServerSocket server) throws IOException {
        List<String> commands = new ArrayList<>();
        try (Socket socket = server.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            reply(writer, "* OK [CAPABILITY IMAP4rev1] mailbox ready");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                if (parts.length < 2) {
                    throw new IOException("Invalid IMAP command: " + line);
                }
                String tag = parts[0];
                String command = parts[1];
                commands.add(command);
                switch (command) {
                    case "CAPABILITY" -> reply(writer, "* CAPABILITY IMAP4rev1");
                    case "LOGIN", "NOOP" -> {
                        // These commands need only the tagged success response.
                    }
                    case "LIST" -> {
                        reply(writer, "* LIST (\\Noinferiors) \"/\" \"INBOX\"");
                        reply(writer, "* LIST (\\Noselect \\HasChildren) \"/\" \"Projects\"");
                    }
                    case "LOGOUT" -> {
                        reply(writer, "* BYE closing connection");
                        reply(writer, tag + " OK LOGOUT completed");
                        return commands;
                    }
                    default -> throw new IOException("Unexpected IMAP command: " + line);
                }
                reply(writer, tag + " OK " + command + " completed");
            }
            throw new IOException("IMAP client disconnected before LOGOUT");
        }
    }

    private static void reply(BufferedWriter writer, String response) throws IOException {
        writer.write(response);
        writer.write("\r\n");
        writer.flush();
    }

    public static final class CustomIMAPFolder extends IMAPFolder {
        private final String construction;

        public CustomIMAPFolder(String fullName, char separator, IMAPStore store, Boolean isNamespace) {
            super(fullName, separator, store, isNamespace);
            construction = "name";
        }

        public CustomIMAPFolder(ListInfo listInfo, IMAPStore store) {
            super(listInfo, store);
            construction = "listing";
        }
    }
}
