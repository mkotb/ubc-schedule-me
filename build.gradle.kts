import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.10"
}

group = "com.mazenk"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "http://jcenter.bintray.com") {
        name = "jcenter"
    }

    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    implementation("org.jsoup:jsoup:1.11.3")
    implementation("com.google.code.gson:gson:2.8.5")
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}