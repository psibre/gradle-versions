import com.google.cloud.tools.jib.gradle.JibExtension
import net.ltgt.gradle.errorprone.errorprone

plugins {
    idea
    id("com.diffplug.spotless") version "6.15.0"
    id("com.google.cloud.tools.jib") version "3.3.1" apply false
    id("com.markelliot.versions") version "0.42.0"
    id("com.palantir.consistent-versions") version "2.12.0"
    id("net.ltgt.errorprone") version "3.0.1" apply false
    id("org.inferred.processors") version "3.7.0" apply false
}

version = "git describe --tags".runCommand().trim() +
    (if (!"git status -s".runCommand().isEmpty()) ".dirty" else "")

task("printVersion") {
    doLast {
        println(rootProject.version)
    }
}

allprojects {
    group = "com.markelliot.gradle.versions"
    version = rootProject.version
}

allprojects {
    apply(plugin = "idea")
    apply(plugin = "com.diffplug.spotless")

    // lives in allprojects because of consistent-versions
    repositories {
        mavenCentral()
    }

    plugins.withType<ApplicationPlugin> {
        apply(plugin = "com.google.cloud.tools.jib")

        val imageName = "${project.name}:${project.version}"
        configure<JibExtension> {
            from {
                image = "azul/zulu-openjdk:17"
            }
            to {
                image = imageName
                tags = setOf("latest")
            }
        }

        tasks.register("docker").get().dependsOn("jibDockerBuild")
    }

    plugins.withType<JavaLibraryPlugin> {
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "org.inferred.processors")

        dependencies {
            "errorprone"("com.google.errorprone:error_prone_core")
            "errorprone"("com.jakewharton.nopen:nopen-checker")
            "compileOnly"("com.jakewharton.nopen:nopen-annotations")
        }

        spotless {
            java {
                googleJavaFormat("1.10.0").aosp()
            }
        }

        tasks.withType<JavaCompile> {
            options.errorprone.disable("UnusedVariable")
        }

        the<JavaPluginExtension>().sourceCompatibility = JavaVersion.VERSION_11

        tasks.withType<Javadoc> {
            (options as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
            options.encoding = "UTF-8"
        }

        tasks["check"].dependsOn("spotlessCheck")
    }

    spotless {
        kotlinGradle {
            ktlint()
        }
    }

    tasks.register("format").get().dependsOn("spotlessApply")
}

fun booleanEnv(envVar: String): Boolean? {
    return System.getenv(envVar)?.toBoolean()
}

fun String.runCommand(): String {
    val proc = ProcessBuilder(*split(" ").toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    proc.waitFor(10, TimeUnit.SECONDS)
    return proc.inputStream.bufferedReader().readText()
}
