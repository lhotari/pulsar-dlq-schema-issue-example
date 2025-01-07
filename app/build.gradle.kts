plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    id("com.ncorti.ktfmt.gradle") version "0.21.0"

    application
}

repositories {
    mavenLocal {
        content {
            includeVersionByRegex(".*", ".*", ".*-SNAPSHOT")
        }
    }
    mavenCentral()
}

val PULSAR_VERSION = "4.0.1"

dependencies {
    implementation("ch.qos.logback:logback-core:1.5.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("org.apache.avro:avro:1.11.4")
    implementation("org.testcontainers:pulsar:1.20.4")

    implementation("org.apache.pulsar:pulsar-client:$PULSAR_VERSION")
    implementation("org.apache.pulsar:pulsar-client-admin:$PULSAR_VERSION")
//    implementation("io.streamnative:pulsar-client:4.0.5.2")
//    implementation("io.streamnative:pulsar-client-admin:4.0.5.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("dlq.issue.AppKt")
    applicationDefaultJvmArgs = listOf("-DPULSAR_VERSION=$PULSAR_VERSION")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named("compileKotlin") {
    dependsOn("ktfmtFormat")
}

configurations {
    all {
        resolutionStrategy {
            eachDependency {
                if ((requested.group == "org.apache.bookkeeper" || requested.group == "io.streamnative") &&
                    requested.name in listOf("circe-checksum", "cpu-affinity", "native-io")
                ) {
                    // Workaround for invalid metadata for Bookkeeper dependencies which contain
                    // <packaging>nar</packaging> in pom.xml
                    artifactSelection {
                        selectArtifact("jar", null, null)
                    }
                } else if (requested.name == "pulsar-client" || requested.name == "pulsar-client-all") {
                    // replace pulsar-client and pulsar-client-all with pulsar-client-original
                    useTarget("${requested.group}:pulsar-client-original:${requested.version}")
                } else if (requested.name == "pulsar-client-admin") {
                    // replace pulsar-client-admin with pulsar-client-admin-original
                    useTarget("${requested.group}:pulsar-client-admin-original:${requested.version}")
                }
            }
        }
    }
}