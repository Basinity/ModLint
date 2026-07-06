plugins {
    java
    application
}

application {
    mainClass = "com.modlint.cli.ModLintCommand"
}

group = "com.modlint"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.yaml:snakeyaml:2.6")
    // Only for its version / version-range parsing, so range semantics match the loader's own.
    implementation("net.fabricmc:fabric-loader:0.19.3") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
