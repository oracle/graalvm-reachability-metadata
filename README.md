# GraalVM Reachability Metadata Repository

This repository provides [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) that lets Java libraries and frameworks work out of the box with [GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/). 

To get out-of-the-box support, use the [GraalVM Gradle Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) or the [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html); they automatically use the [reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/) from this repository.

---

### 🔎 Check if Your Library or Framework Is Supported

To quickly check whether reachability metadata exists for a specific library, you can run the following command directly from your terminal (works on **Linux** and **macOS**, or on **Windows** with Git Bash / WSL):
```bash
curl -sSL https://raw.githubusercontent.com/oracle/graalvm-reachability-metadata/master/check-library-support.sh | bash -s "<groupId>:<artifactId>:<version>"
```

For a broader overview of supported libraries and frameworks, you can visit [this page](https://www.graalvm.org/native-image/libraries-and-frameworks/). It lists libraries and frameworks that are tested and ready for GraalVM Native Image.  

If you’d like yours to appear there as well, open a pull request updating [this JSON file](https://github.com/oracle/graalvm-reachability-metadata/blob/master/metadata/library-and-framework-list.json).
Before submitting a pull request, please read [this guide](docs/CONTRIBUTING.md#tested-libraries-and-frameworks).

### 📚 Request Support for a New Library

Open a [library-request ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=01_support_new_library.yml), include the Maven coordinates, and our automation will take it from there (🤖).

### 🛠️ Request an Update to an Existing Library

Open an [update ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml) and include the Maven coordinates of the library that needs changes.

---

### Tested Libraries

| Library | Description |
| --- | --- |
| `berkeleydb:je` | Berkeley DB Java Edition is an embedded key-value database library for Java applications. |
| `ch.qos.logback.contrib:logback-jackson` | Logback Jackson adds Jackson-based JSON support for Logback appenders and layouts. |
| `ch.qos.logback.contrib:logback-json-classic` | Logback JSON Classic provides JSON formatting support for Logback Classic logging events. |
| `ch.qos.logback:logback-classic` | Logback Classic is the SLF4J-native implementation module of the Logback logging framework. |
| `com.atomikos:transactions` | Atomikos Transactions provides JTA/XA transaction management for Java applications. |
| `com.ecwid.consul:consul-api` | consul-api is a Java client for interacting with HashiCorp Consul services and KV storage. |
| `com.fasterxml.jackson.core:jackson-databind` | jackson-databind maps Java objects to and from JSON using Jackson. |
| `com.github.ben-manes.caffeine:caffeine` | Caffeine is a high-performance Java caching library. |
| `com.github.luben:zstd-jni` | zstd-jni provides Java bindings for Facebook Zstandard compression. |
| `com.google.protobuf:protobuf-java-util` | protobuf-java-util adds utility APIs for working with Protocol Buffers in Java. |
| `com.graphql-java:graphql-java` | graphql-java is a Java implementation of the GraphQL specification. |
| `com.graphql-java:graphql-java-extended-validation` | graphql-java-extended-validation adds extra schema and query validation rules for graphql-java. |
| `com.h2database:h2` | H2 is a lightweight relational database engine written in Java. |
| `com.hazelcast:hazelcast` | Hazelcast is an in-memory data grid and distributed computing platform for Java. |
| `com.itextpdf:forms` | iText Forms provides PDF AcroForm creation and manipulation capabilities. |
| `com.itextpdf:io` | iText IO contains low-level input/output utilities used by iText PDF processing modules. |
| `com.itextpdf:kernel` | iText Kernel provides the low-level core APIs for creating and manipulating PDF documents. |
| `com.itextpdf:layout` | iText Layout provides high-level document layout APIs for PDF generation. |
| `com.itextpdf:svg` | iText SVG adds support for converting SVG content into PDF graphics. |
| `com.microsoft.sqlserver:mssql-jdbc` | mssql-jdbc is the Microsoft JDBC driver for SQL Server. |
| `com.mysql:mysql-connector-j` | MySQL Connector/J is the official JDBC driver for MySQL. |
| `com.sun.mail:jakarta.mail` | jakarta.mail is a Java API implementation for sending and receiving email. |
| `com.zaxxer:HikariCP` | HikariCP is a fast and lightweight JDBC connection pool. |
| `commons-logging:commons-logging` | Apache Commons Logging provides a thin logging abstraction layer for Java. |
| `io.grpc:grpc-core` | grpc-core contains the foundational runtime components for gRPC Java. |
| `io.grpc:grpc-netty` | grpc-netty provides a Netty-based transport implementation for gRPC Java. |
| `io.jsonwebtoken:jjwt-gson` | jjwt-gson adds Gson serialization support for JJWT JSON Web Tokens. |
| `io.jsonwebtoken:jjwt-jackson` | jjwt-jackson adds Jackson serialization support for JJWT JSON Web Tokens. |
| `io.jsonwebtoken:jjwt-orgjson` | jjwt-orgjson adds org.json serialization support for JJWT JSON Web Tokens. |
| `io.nats:jnats` | jnats is the official Java client for the NATS messaging system. |
| `io.netty:netty-buffer` | netty-buffer provides Netty byte buffer implementations and utilities. |
| `io.netty:netty-codec-http` | netty-codec-http provides HTTP codecs for Netty network applications. |
| `io.netty:netty-codec-http2` | netty-codec-http2 provides HTTP/2 codec support for Netty. |
| `io.netty:netty-common` | netty-common provides core utility classes used across Netty modules. |
| `io.netty:netty-handler` | netty-handler provides channel handlers and SSL/TLS support for Netty. |
| `io.netty:netty-resolver-dns` | netty-resolver-dns provides asynchronous DNS resolution for Netty. |
| `io.netty:netty-transport` | netty-transport provides Netty transport and channel abstractions. |
| `io.opentelemetry:opentelemetry-exporter-jaeger` | opentelemetry-exporter-jaeger exports OpenTelemetry traces to Jaeger. |
| `io.opentelemetry:opentelemetry-exporter-logging` | opentelemetry-exporter-logging writes OpenTelemetry telemetry data to logs. |
| `io.opentelemetry:opentelemetry-exporter-otlp` | opentelemetry-exporter-otlp exports OpenTelemetry data using OTLP. |
| `io.opentelemetry:opentelemetry-exporter-zipkin` | opentelemetry-exporter-zipkin exports OpenTelemetry traces to Zipkin. |
| `io.opentelemetry:opentelemetry-sdk-metrics` | opentelemetry-sdk-metrics provides the metrics SDK implementation for OpenTelemetry Java. |
| `io.opentelemetry:opentelemetry-sdk-trace` | opentelemetry-sdk-trace provides the tracing SDK implementation for OpenTelemetry Java. |
| `io.undertow:undertow-core` | undertow-core is the core module of the Undertow web server. |
| `jakarta.servlet:jakarta.servlet-api` | jakarta.servlet-api defines the Jakarta Servlet API interfaces and contracts. |
| `javax.cache:cache-api` | cache-api defines the JCache standard API for Java caching. |
| `log4j:log4j` | log4j is Apache Log4j 1.x for Java logging. |
| `mysql:mysql-connector-java` | mysql-connector-java is the legacy MySQL JDBC driver artifact. |
| `net.java.dev.jna:jna` | JNA provides Java access to native shared libraries without JNI code. |
| `org.apache.activemq:activemq-broker` | activemq-broker provides broker runtime components for Apache ActiveMQ Classic. |
| `org.apache.activemq:activemq-client` | activemq-client provides client APIs for Apache ActiveMQ Classic messaging. |
| `org.apache.activemq:artemis-jms-client` | artemis-jms-client provides JMS client support for Apache ActiveMQ Artemis. |
| `org.apache.commons:commons-compress` | commons-compress provides APIs for working with archive and compression formats. |
| `org.apache.commons:commons-dbcp2` | commons-dbcp2 provides database connection pooling for Java applications. |
| `org.apache.commons:commons-pool2` | commons-pool2 provides generic object pooling utilities. |
| `org.apache.httpcomponents:httpclient` | Apache HttpClient provides HTTP client functionality for Java. |
| `org.apache.kafka:kafka-clients` | kafka-clients provides producer, consumer, and admin clients for Apache Kafka. |
| `org.apache.kafka:kafka-streams` | kafka-streams provides a stream processing library on top of Apache Kafka. |
| `org.apache.santuario:xmlsec` | Apache Santuario XML Security provides XML Signature and Encryption support. |
| `org.apache.tomcat.embed:tomcat-embed-core` | tomcat-embed-core provides embedded Apache Tomcat server runtime components. |
| `org.apache.tomcat:tomcat-jdbc` | tomcat-jdbc is Apache Tomcat's JDBC connection pool implementation. |
| `org.apache.zookeeper:zookeeper` | zookeeper is the Java client and core library for Apache ZooKeeper coordination. |
| `org.bouncycastle:bcpkix-jdk15on` | Bouncy Castle PKIX for jdk15on provides PKIX, CMS, and certificate support. |
| `org.bouncycastle:bcpkix-jdk15to18` | Bouncy Castle PKIX for jdk15to18 provides PKIX APIs for JDK 15-18 environments. |
| `org.bouncycastle:bcpkix-jdk18on` | Bouncy Castle PKIX for jdk18on provides PKIX APIs for JDK 18 and later. |
| `org.eclipse.angus:jakarta.mail` | Eclipse Angus Jakarta Mail is a Jakarta Mail implementation. |
| `org.eclipse.jetty:jetty-client` | jetty-client provides asynchronous HTTP client capabilities for Jetty. |
| `org.eclipse.jetty:jetty-server` | jetty-server provides the core HTTP server for Jetty. |
| `org.eclipse.jetty:jetty-util` | jetty-util provides utility classes used by Jetty components. |
| `org.eclipse.jgit:org.eclipse.jgit` | JGit is a pure Java implementation of Git. |
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3` | Eclipse Paho MQTT v3 client provides MQTT 3.x client functionality for Java. |
| `org.eclipse.paho:org.eclipse.paho.mqttv5.client` | Eclipse Paho MQTT v5 client provides MQTT 5 client functionality for Java. |
| `org.ehcache:ehcache` | Ehcache is a standards-based Java caching library. |
| `org.example:library` | org.example:library is a sample placeholder artifact used for repository testing. |
| `org.flywaydb:flyway-core` | Flyway Core provides database schema migration support. |
| `org.flywaydb:flyway-database-postgresql` | flyway-database-postgresql adds PostgreSQL-specific support to Flyway. |
| `org.flywaydb:flyway-sqlserver` | flyway-sqlserver adds SQL Server-specific support to Flyway. |
| `org.freemarker:freemarker` | FreeMarker is a Java template engine for generating text output. |
| `org.glassfish.jaxb:jaxb-runtime` | jaxb-runtime provides the runtime implementation for JAXB binding. |
| `org.hdrhistogram:HdrHistogram` | HdrHistogram records and analyzes latency and throughput distributions with high precision. |
| `org.hibernate.orm:hibernate-core` | hibernate-core is the main ORM engine module for Hibernate ORM. |
| `org.hibernate.orm:hibernate-envers` | hibernate-envers adds auditing and versioning support for Hibernate entities. |
| `org.hibernate.reactive:hibernate-reactive-core` | hibernate-reactive-core provides reactive, non-blocking persistence for Hibernate. |
| `org.hibernate.validator:hibernate-validator` | hibernate-validator is the reference implementation of Jakarta Bean Validation. |
| `org.hibernate:hibernate-core` | hibernate-core is the core ORM module for legacy Hibernate group coordinates. |
| `org.hibernate:hibernate-spatial` | hibernate-spatial adds geospatial data type support to Hibernate ORM. |
| `org.jboss.logging:jboss-logging` | jboss-logging provides a logging facade used by JBoss and related projects. |
| `org.jboss.spec.javax.servlet:jboss-servlet-api_4.0_spec` | jboss-servlet-api_4.0_spec provides the Javax Servlet 4.0 API specification artifact. |
| `org.jctools:jctools-core` | jctools-core provides high-performance concurrent data structures for Java. |
| `org.jetbrains.kotlin:kotlin-reflect` | kotlin-reflect provides runtime reflection support for Kotlin programs. |
| `org.jetbrains.kotlin:kotlin-stdlib` | kotlin-stdlib provides the standard runtime library for Kotlin. |
| `org.jline:jline` | jline provides console input editing and terminal interaction for Java CLIs. |
| `org.jline:jline-console` | jline-console provides higher-level console features built on JLine. |
| `org.jline:jline-terminal` | jline-terminal provides terminal abstraction and native terminal integration for JLine. |
| `org.jooq:jooq` | jOOQ provides a type-safe fluent API for building SQL in Java. |
| `org.liquibase:liquibase-core` | Liquibase Core provides database schema change management capabilities. |
| `org.mariadb.jdbc:mariadb-java-client` | mariadb-java-client is the JDBC driver for MariaDB and compatible databases. |
| `org.mariadb:r2dbc-mariadb` | r2dbc-mariadb provides an R2DBC reactive driver for MariaDB. |
| `org.mockito:mockito-core` | mockito-core provides mocking utilities for unit testing in Java. |
| `org.opengauss:opengauss-jdbc` | opengauss-jdbc is the JDBC driver for openGauss databases. |
| `org.postgresql:postgresql` | postgresql is the official PostgreSQL JDBC driver. |
| `org.quartz-scheduler:quartz` | Quartz is a job scheduling library for Java applications. |
| `org.rocksdb:rocksdbjni` | rocksdbjni provides Java bindings to the RocksDB embedded key-value store. |
| `org.testcontainers:testcontainers` | Testcontainers provides disposable Docker-based dependencies for integration testing. |
| `org.thymeleaf.extras:thymeleaf-extras-springsecurity6` | thymeleaf-extras-springsecurity6 integrates Thymeleaf templates with Spring Security 6. |
| `org.thymeleaf:thymeleaf` | Thymeleaf is a server-side Java template engine for web and standalone applications. |
| `org.thymeleaf:thymeleaf-spring6` | thymeleaf-spring6 provides Spring 6 integration for Thymeleaf. |

### Contributing

We welcome contributions from the community. Thank you!

Before submitting a pull request, please [open a ticket](https://github.com/oracle/graalvm-reachability-metadata/issues/new?template=02_update_existing_library.yml), mark that you want to fix it yourself, and [review our contribution guide](docs/CONTRIBUTING.md).

### Further Information

1. Continuous integration is described in [CI.md](docs/CI.md).
2. Pull request review guidelines are in [REVIEWING.md](docs/REVIEWING.md).
3. Development workflow is described in [DEVELOPING.md](docs/DEVELOPING.md).

---
Built with love by the community and the [GraalVM](https://www.graalvm.org/), [Spring](https://spring.io/projects/spring-boot), and [Micronaut](https://micronaut.io/) teams.
