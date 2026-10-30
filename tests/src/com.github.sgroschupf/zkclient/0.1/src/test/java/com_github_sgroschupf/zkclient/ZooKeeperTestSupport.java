/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_sgroschupf.zkclient;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.server.NIOServerCnxn.Factory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public abstract class ZooKeeperTestSupport {
    @TempDir
    Path directory;

    protected ZkClient client;
    protected String servers;
    private ZooKeeperServer server;
    private Factory factory;

    @BeforeEach
    @Timeout(60)
    void startServerAndClient() throws Exception {
        Path snapshots = Files.createDirectories(directory.resolve("snapshots"));
        Path logs = Files.createDirectories(directory.resolve("logs"));
        server = new ZooKeeperServer(snapshots.toFile(), logs.toFile(), 2000);
        factory = new Factory(new InetSocketAddress("127.0.0.1", 0));
        factory.startup(server);
        servers = "127.0.0.1:" + factory.getLocalPort();
        client = new ZkClient(servers, 30000, 30000);
    }

    @AfterEach
    @Timeout(60)
    void closeClientAndServer() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            try {
                if (factory != null) {
                    factory.shutdown();
                }
            } finally {
                if (server != null) {
                    server.shutdown();
                }
            }
        }
    }
}
