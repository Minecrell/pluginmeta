import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java

    signing
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.4"

    id("net.minecrell.licenser") version "0.3"
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "com.jfrog.bintray")
    apply(plugin = "net.minecrell.licenser")
    apply(plugin = "signing")

    repositories {
        jcenter()
        maven("https://repo.spongepowered.org/maven")
        maven("https://repo-new.spongepowered.org/maven")
    }

    val sourceOutput by configurations.registering
    val main by sourceSets

    dependencies {
        compileOnly("org.checkerframework:checker-qual:3.4.1")

        main.allSource.srcDirs.forEach {
            add(sourceOutput.name, project.files(it.relativeTo(project.projectDir).path))
        }

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.2.0")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.2.0")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }


    val jar by tasks.existing(Jar::class) {
        manifest {
            attributes(mapOf(
                    "Specification-Title" to "plugin-spi",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to archiveVersion, // We are version 1 of ourselves
                    "Created-By" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})"
            ))
        }
    }

    val javadoc by tasks.existing(Javadoc::class) {
        options {
            encoding = "UTF-8"
            charset("UTF-8")
            isFailOnError = false
            (this as StandardJavadocDocletOptions).apply {
                links?.addAll(
                        mutableListOf(
                                "http://www.slf4j.org/apidocs/",
                                "https://google.github.io/guava/releases/21.0/api/docs/",
                                "https://google.github.io/guice/api-docs/4.1/javadoc/",
                                "http://asm.ow2.org/asm50/javadoc/user/",
                                "https://docs.oracle.com/javase/8/docs/api/"
                        )
                )
            }
        }
    }

    val javadocJar by tasks.registering(Jar::class) {
        group = "build"
        classifier = "javadoc"
        from(javadoc)
    }

    tasks.getByName<Test>("test") {
        useJUnitPlatform()

        testLogging {
            // Always print full stack trace if something goes wrong in the unit tests
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = true
        }
    }

    val sourceJar by tasks.registering(Jar::class) {
        classifier = "sources"
        group = "build"
        from(sourceOutput)
    }


    artifacts {
        add("archives", sourceJar)
        add("archives", javadocJar)
    }

    license {
        header = rootProject.file("HEADER.txt")
        newLine = false

        ext["name"] = rootProject.name
        ext["organization"] = rootProject.property("organization")
    }
    signing {
        val signingKey: String? by project
        val signingPassword: String? by project
        // TODO - get signing jar to configure with ci/cd
//    useInMemoryPgpKeys(signingKey, signingPassword)
//    sign(tasks["jar"])
    }
    val spongeSnapshotRepo: String? by project
    val spongeReleaseRepo: String? by project

    tasks.withType<PublishToMavenLocal>().configureEach {
        onlyIf {
            publication == publishing.publications["sponge"]
        }
    }
    val url: String by project
    val description: String by project
    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                this.setUrl(uri("https://maven.pkg.github.com/spongepowered/plugin-meta"))
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
            // Set by the build server
            maven {
                name = "spongeRepo"
                val repoUrl = if ((version as String).endsWith("-SNAPSHOT")) spongeSnapshotRepo else spongeReleaseRepo
                repoUrl?.apply {
                    setUrl(uri(this))
                }
                val spongeUsername: String? by project
                val spongePassword: String? by project
                credentials {
                    username = spongeUsername ?: System.getenv("ORG_GRADLE_PROJECT_spongeUsername")
                    password = spongePassword ?: System.getenv("ORG_GRADLE_PROJECT_spongePassword")
                }
            }
        }

        publications {

            register("sponge", MavenPublication::class) {
                artifact(jar.get())
                artifact(sourceJar.get())
                artifact(javadocJar.get())
                pom {
                    this.name.set("plugin-meta")
                    setDescription(description)
                    this.url.set(url)

                    licenses {
                        license {
                            this.name.set("MIT")
                            this.url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/SpongePowered/plugin-meta.git")
                        developerConnection.set("scm:git:ssh://github.com/SpongePowered/plugin-meta.git")
                        this.url.set(url)
                    }
                }
            }
        }

    }


}

dependencies {
    compile("com.google.guava:guava:21.0")
}

subprojects {
    group = "${rootProject.group}.${rootProject.name}"

    dependencies {
        compile(rootProject)
    }
}
