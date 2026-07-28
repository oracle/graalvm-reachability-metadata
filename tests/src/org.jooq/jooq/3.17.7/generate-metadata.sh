#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

./gradlew clean
./gradlew test --tests "org_jooq.jooq.JooqTest" -Pagent
./gradlew updateGeneratedMetadata
