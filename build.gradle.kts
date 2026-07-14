plugins {
    java
    application
}

application {
    mainClass = "com.modlint.cli.ModLintCommand"
}

group = "com.modlint"
version = "0.1.0"

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
    implementation("io.javalin:javalin:6.7.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.yaml:snakeyaml:2.6")
    // Only for its version / version-range parsing, so range semantics match the loader's own.
    implementation("net.fabricmc:fabric-loader:0.19.3") {
        isTransitive = false
    }
    // TOML parsing for mods.toml / neoforge.mods.toml; the same library Forge and NeoForge use.
    implementation("com.electronwill.night-config:toml:3.8.2")
    // Only for Maven version-range parsing, the range syntax Forge and NeoForge dependencies use.
    implementation("org.apache.maven:maven-artifact:3.9.9") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// The web UI builds through npm; only the server jar depends on it, so plain build/test stay npm-free.
val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val npmInstall by tasks.registering(Exec::class) {
    workingDir = file("web")
    commandLine(npm, "install", "--no-fund", "--no-audit")
    inputs.files("web/package.json", "web/package-lock.json")
    outputs.dir("web/node_modules")
}

val buildWeb by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = file("web")
    commandLine(npm, "run", "build")
    inputs.files(fileTree("web") {
        exclude("node_modules/**", "dist/**")
    })
    outputs.dir("web/dist")
}

val serverJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds the self-contained web server jar (engine + API + browser UI)."
    dependsOn(buildWeb)
    archiveBaseName = "modlint-server"
    manifest {
        attributes["Main-Class"] = "com.modlint.web.ModLintServer"
        attributes["Multi-Release"] = "true"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    }
    from("web/dist") {
        into("web")
    }
}
