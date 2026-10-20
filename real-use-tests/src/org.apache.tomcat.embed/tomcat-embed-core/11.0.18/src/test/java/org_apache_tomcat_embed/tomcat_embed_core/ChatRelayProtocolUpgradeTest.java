/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a chat relay over the servlet HTTP protocol upgrade, where a servlet answers an
 * `Upgrade: chatline/1` request with `HttpServletRequest.upgrade`, Tomcat instantiates the
 * `HttpUpgradeHandler` and hands it a `WebConnection` driven by non-blocking `ReadListener`
 * and `WriteListener` I/O on the HTTP/1.1 NIO connector.
 *
 * A small team chat speaks its own line protocol on the application's HTTP port so that it
 * passes the same proxies as the web pages. Plain requests to a room are refused with
 * `426 Upgrade Required`; an upgrade request switches the connection with `101`, after
 * which members see who joins and leaves, relay messages to everyone in their room, list
 * the room's members and quit. Rooms are isolated from each other, and a client that drops
 * its socket without quitting is removed from its room and its handler is destroyed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatRelayProtocolUpgradeTest {

    private static final String PROTOCOL = "chatline/1";

    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private final ChatRooms rooms = new ChatRooms();

    private Tomcat tomcat;

    private Path baseDir;

    private int port;

    @BeforeAll
    void startServer() throws IOException, LifecycleException {
        baseDir = Files.createTempDirectory("chat-relay-tomcat");
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("/relay", baseDir.toString());
        Tomcat.addServlet(context, "rooms", new RoomServlet(rooms));
        context.addServletMappingDecoded("/room/*", "rooms");

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    void stopServer() throws LifecycleException, IOException {
        tomcat.stop();
        tomcat.destroy();
        try (var paths = Files.walk(baseDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void refusesAPlainRequestWithUpgradeRequired() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/relay/room/lobby"))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(426);
        assertThat(response.headers().firstValue("Upgrade")).hasValue(PROTOCOL);
        assertThat(response.body()).isEqualTo("Speak " + PROTOCOL + " to join room lobby\n");
    }

    @Test
    void refusesAnUnknownProtocolOverARawConnection() throws Exception {
        try (Socket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            out.write("""
                    GET /relay/room/lobby?nick=eve HTTP/1.1\r
                    Host: localhost\r
                    Upgrade: smoke-signals/2\r
                    Connection: Upgrade\r
                    Content-Length: 0\r
                    \r
                    """.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader reader = reader(socket);

            assertThat(reader.readLine()).startsWith("HTTP/1.1 426");
            assertThat(readHeaders(reader)).containsEntry("upgrade", PROTOCOL);
        }
    }

    @Test
    void relaysMessagesBetweenMembersOfARoom() throws Exception {
        try (ChatClient ada = ChatClient.join(port, "lobby", "ada")) {
            assertThat(ada.handshakeHeaders).containsEntry("upgrade", PROTOCOL);
            assertThat(ada.handshakeHeaders.get("connection")).containsIgnoringCase("upgrade");
            assertThat(ada.readLine()).isEqualTo("WELCOME ada to lobby (1 online)");

            ChatClient bob = ChatClient.join(port, "lobby", "bob");
            assertThat(bob.readLine()).isEqualTo("WELCOME bob to lobby (2 online)");
            assertThat(ada.readLine()).isEqualTo("JOINED bob");

            ada.send("SAY hello, team");
            assertThat(ada.readLine()).isEqualTo("[ada] hello, team");
            assertThat(bob.readLine()).isEqualTo("[ada] hello, team");

            bob.send("SAY hi ada see you at five");
            assertThat(bob.readLine()).isEqualTo("[bob] hi ada see you at five");
            assertThat(ada.readLine()).isEqualTo("[bob] hi ada see you at five");

            bob.send("WHO");
            assertThat(bob.readLine()).isEqualTo("MEMBERS ada,bob");

            bob.send("DANCE");
            assertThat(bob.readLine()).isEqualTo("ERROR unknown command DANCE");

            bob.send("QUIT");
            assertThat(bob.readLine()).isEqualTo("BYE bob");
            assertThat(bob.readLine()).isNull();
            bob.close();
            assertThat(ada.readLine()).isEqualTo("LEFT bob");
            assertThat(rooms.awaitDestroyed("bob")).isTrue();

            ada.send("WHO");
            assertThat(ada.readLine()).isEqualTo("MEMBERS ada");
        }
    }

    @Test
    void keepsRoomsIsolatedFromEachOther() throws Exception {
        try (ChatClient carol = ChatClient.join(port, "kitchen", "carol");
                ChatClient dan = ChatClient.join(port, "garden", "dan")) {
            assertThat(carol.readLine()).isEqualTo("WELCOME carol to kitchen (1 online)");
            assertThat(dan.readLine()).isEqualTo("WELCOME dan to garden (1 online)");

            carol.send("SAY the soup is ready");
            assertThat(carol.readLine()).isEqualTo("[carol] the soup is ready");

            dan.send("WHO");
            assertThat(dan.readLine()).isEqualTo("MEMBERS dan");
            carol.send("WHO");
            assertThat(carol.readLine()).isEqualTo("MEMBERS carol");
        }
    }

    @Test
    void removesAMemberWhoseSocketDrops() throws Exception {
        try (ChatClient frank = ChatClient.join(port, "garage", "frank")) {
            assertThat(frank.readLine()).isEqualTo("WELCOME frank to garage (1 online)");

            ChatClient gina = ChatClient.join(port, "garage", "gina");
            assertThat(gina.readLine()).isEqualTo("WELCOME gina to garage (2 online)");
            assertThat(frank.readLine()).isEqualTo("JOINED gina");

            gina.close();

            assertThat(frank.readLine()).isEqualTo("LEFT gina");
            assertThat(rooms.awaitDestroyed("gina")).isTrue();
            frank.send("WHO");
            assertThat(frank.readLine()).isEqualTo("MEMBERS frank");
        }
    }

    private Socket connect() throws IOException {
        return openSocket(port);
    }

    private static Socket openSocket(int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("localhost", port), READ_TIMEOUT_MILLIS);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        return socket;
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        return headers;
    }

    /// A chat member's side of the protocol: the upgrade handshake, then UTF-8 lines.
    static final class ChatClient implements AutoCloseable {

        private final Socket socket;

        private final BufferedReader reader;

        final Map<String, String> handshakeHeaders;

        private ChatClient(Socket socket, BufferedReader reader, Map<String, String> handshakeHeaders) {
            this.socket = socket;
            this.reader = reader;
            this.handshakeHeaders = handshakeHeaders;
        }

        static ChatClient join(int port, String room, String nick) throws IOException {
            Socket socket = openSocket(port);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /relay/room/" + room + "?nick=" + nick + " HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Upgrade: " + PROTOCOL + "\r\n"
                    + "Connection: Upgrade\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader reader = reader(socket);
            String statusLine = reader.readLine();
            assertThat(statusLine).startsWith("HTTP/1.1 101");
            return new ChatClient(socket, reader, readHeaders(reader));
        }

        void send(String line) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        String readLine() throws IOException {
            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    /// Room membership shared by every upgraded connection, plus a destroy latch per member.
    static final class ChatRooms {

        private final Map<String, Set<ChatLineHandler>> members = new ConcurrentHashMap<>();

        private final Map<String, CountDownLatch> destroyed = new ConcurrentHashMap<>();

        synchronized int join(ChatLineHandler handler) {
            Set<ChatLineHandler> room = members.computeIfAbsent(handler.room, name -> ConcurrentHashMap.newKeySet());
            room.add(handler);
            for (ChatLineHandler other : room) {
                if (other != handler) {
                    other.deliver("JOINED " + handler.nick);
                }
            }
            return room.size();
        }

        synchronized void leave(ChatLineHandler handler) {
            Set<ChatLineHandler> room = members.getOrDefault(handler.room, Set.of());
            if (room.remove(handler)) {
                room.forEach(other -> other.deliver("LEFT " + handler.nick));
            }
        }

        synchronized void broadcast(ChatLineHandler sender, String text) {
            members.getOrDefault(sender.room, Set.of())
                    .forEach(member -> member.deliver("[" + sender.nick + "] " + text));
        }

        synchronized String who(String room) {
            return members.getOrDefault(room, Set.of()).stream()
                    .map(member -> member.nick)
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        void markDestroyed(String nick) {
            destroyed.computeIfAbsent(nick, key -> new CountDownLatch(1)).countDown();
        }

        boolean awaitDestroyed(String nick) throws InterruptedException {
            return destroyed.computeIfAbsent(nick, key -> new CountDownLatch(1))
                    .await(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /// Answers plain requests with 426 and switches `chatline/1` requests to the handler.
    static final class RoomServlet extends HttpServlet {

        private final ChatRooms rooms;

        RoomServlet(ChatRooms rooms) {
            this.rooms = rooms;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            String room = request.getPathInfo().substring(1);
            String upgrade = request.getHeader("Upgrade");
            if (!PROTOCOL.equalsIgnoreCase(upgrade)) {
                response.setStatus(426);
                response.setHeader("Upgrade", PROTOCOL);
                response.setHeader("Connection", "Upgrade");
                response.setContentType("text/plain");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("Speak " + PROTOCOL + " to join room " + room + "\n");
                return;
            }
            response.setHeader("Upgrade", PROTOCOL);
            response.setHeader("Connection", "Upgrade");
            ChatLineHandler handler = request.upgrade(ChatLineHandler.class);
            handler.enter(rooms, room, request.getParameter("nick"));
        }
    }

    /// One member's upgraded connection, reading command lines and writing queued replies.
    public static final class ChatLineHandler implements HttpUpgradeHandler {

        private final Queue<byte[]> outbox = new ArrayDeque<>();

        private final ByteArrayOutputStream pendingLine = new ByteArrayOutputStream();

        private final AtomicBoolean gone = new AtomicBoolean();

        private ChatRooms rooms;

        private String room;

        private String nick;

        private WebConnection connection;

        private ServletOutputStream output;

        private boolean closeAfterFlush;

        public ChatLineHandler() {
        }

        void enter(ChatRooms chatRooms, String roomName, String nickname) {
            this.rooms = chatRooms;
            this.room = roomName;
            this.nick = nickname;
        }

        @Override
        public void init(WebConnection webConnection) {
            this.connection = webConnection;
            try {
                output = webConnection.getOutputStream();
                ServletInputStream input = webConnection.getInputStream();
                output.setWriteListener(new OutboxWriter());
                int online = rooms.join(this);
                deliver("WELCOME " + nick + " to " + room + " (" + online + " online)");
                input.setReadListener(new CommandReader(input));
            } catch (IOException e) {
                hangUp();
            }
        }

        @Override
        public void destroy() {
            hangUp();
            rooms.markDestroyed(nick);
        }

        void deliver(String line) {
            synchronized (this) {
                outbox.add((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            flush();
        }

        private void flush() {
            boolean failed = false;
            synchronized (this) {
                try {
                    boolean wrote = false;
                    while (!outbox.isEmpty() && output.isReady()) {
                        output.write(outbox.poll());
                        wrote = true;
                    }
                    if (wrote && output.isReady()) {
                        output.flush();
                    }
                    if (outbox.isEmpty() && closeAfterFlush && output.isReady()) {
                        closeAfterFlush = false;
                        connection.close();
                    }
                } catch (Exception e) {
                    failed = true;
                }
            }
            if (failed) {
                hangUp();
            }
        }

        private void handle(String command) {
            if (command.startsWith("SAY ")) {
                rooms.broadcast(this, command.substring(4));
            } else if (command.equals("WHO")) {
                deliver("MEMBERS " + rooms.who(room));
            } else if (command.equals("QUIT")) {
                if (gone.compareAndSet(false, true)) {
                    rooms.leave(this);
                }
                synchronized (this) {
                    closeAfterFlush = true;
                }
                deliver("BYE " + nick);
            } else {
                deliver("ERROR unknown command " + command);
            }
        }

        private void hangUp() {
            if (gone.compareAndSet(false, true)) {
                rooms.leave(this);
                try {
                    connection.close();
                } catch (Exception e) {
                    // The peer is already gone; nothing is left to release.
                }
            }
        }

        private final class OutboxWriter implements WriteListener {

            @Override
            public void onWritePossible() {
                flush();
            }

            @Override
            public void onError(Throwable throwable) {
                hangUp();
            }
        }

        private final class CommandReader implements ReadListener {

            private final ServletInputStream input;

            private final byte[] buffer = new byte[512];

            CommandReader(ServletInputStream input) {
                this.input = input;
            }

            @Override
            public void onDataAvailable() throws IOException {
                while (input.isReady()) {
                    int read = input.read(buffer);
                    if (read == -1) {
                        hangUp();
                        return;
                    }
                    for (int i = 0; i < read; i++) {
                        if (buffer[i] == '\n') {
                            handle(pendingLine.toString(StandardCharsets.UTF_8));
                            pendingLine.reset();
                        } else {
                            pendingLine.write(buffer[i]);
                        }
                    }
                }
            }

            @Override
            public void onAllDataRead() {
                hangUp();
            }

            @Override
            public void onError(Throwable throwable) {
                hangUp();
            }
        }
    }
}
