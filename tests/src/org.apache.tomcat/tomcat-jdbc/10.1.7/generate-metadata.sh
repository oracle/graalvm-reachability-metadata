#!/usr/bin/env bash

./gradlew deleteGeneratedMetadata
./gradlew test --tests "org_apache_tomcat.tomcat_jdbc.TomcatJdbcTest" -Pagent metadataCopy
./gradlew test --tests "org_apache_tomcat.tomcat_jdbc.DriverTest" -Pagent metadataCopy
