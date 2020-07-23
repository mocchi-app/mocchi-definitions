import com.google.protobuf.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.ajoberstar.grgit.Grgit

plugins {
    idea
    id("java-library")
    id("maven-publish")

    id("com.google.protobuf") version "0.8.11"
    kotlin("jvm") version "1.3.61"
    id("org.ajoberstar.grgit") version "4.0.1"
}

description = "Protobuf Registry"
group = "org.mocchi"
val grgit = Grgit.open()
val commit = grgit.head().abbreviatedId
version = commit

val protoDir: File = sourceSets["main"].proto.srcDirs.first()

val grpcVersion = "1.27.2"
val protoVersion = File("$protoDir/prototool.yaml").useLines { lines ->
    lines.first { it.contains("version:") }.substringAfter(':').trim()
}

dependencies {
    api(platform("com.google.protobuf:protobuf-bom:$protoVersion"))
    api("com.google.protobuf:protobuf-java")
    api("com.google.protobuf:protobuf-java-util")
    api("com.google.api.grpc:proto-google-common-protos:1.17.0")

    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))

    compileOnly(platform("io.grpc:grpc-bom:$grpcVersion"))
    compileOnly("io.grpc:grpc-protobuf")
    compileOnly("io.grpc:grpc-stub")

    val junitVersion = "5.6.0"
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly(platform("org.junit:junit-bom:$junitVersion"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

java {
    registerFeature("grpc") {
        usingSourceSet(sourceSets.main.get())
    }

    dependencies {
        "grpcApi"(platform("io.grpc:grpc-bom:$grpcVersion"))
        "grpcApi"("io.grpc:grpc-netty-shaded")
        "grpcApi"("io.grpc:grpc-protobuf")
        "grpcApi"("io.grpc:grpc-stub")
    }

    withSourcesJar()
}

val protoJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles a JAR containing the Protobuf files."
    archiveClassifier.set("proto")
    from(sourceSets["main"].proto) {
        exclude("**/*.md", "**/*.yaml")
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            artifactId = "mocchi-definitions"
            from(components["java"])
            artifact(protoJar.get())
            suppressAllPomMetadataWarnings()
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protoVersion"
    }

    plugins {
        id("grpc-java") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc-java") {
                    outputSubDir = "java"
                }
            }
        }
    }
}

tasks {

    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = true
            freeCompilerArgs = listOf("-progressive", "-Xassertions=jvm", "-Xjsr305=strict")
            jvmTarget = "1.8"
        }
    }

    jar {
        // Do not include documentation and our prototool configuration.
        exclude("**/*.md", "**/*.yaml")
    }

    javadoc {
        enabled = false
    }

    test {
        useJUnitPlatform()
    }

    register("generate") {
        group = "generation"
        description = "Runs all code generators defined for the repo"
    }
}
