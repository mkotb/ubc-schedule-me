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
    implementation("org.jetbrains.exposed:exposed:0.13.2")

    // http
    implementation("org.jsoup:jsoup:1.11.3")
    implementation("io.javalin:javalin:2.8.0")

    // deserialization
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("com.squareup.moshi:moshi:1.8.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.8.0")
    implementation("com.squareup.moshi:moshi-adapters:1.8.0")

    // google maps api
    implementation("com.google.maps:google-maps-services:0.9.3")

    // drivers
    implementation("com.h2database:h2:1.4.197")
    implementation("org.postgresql:postgressql:42.2.2")

    implementation("org.slf4j:slf4j-simple:1.7.26")

    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}