import org.gradle.api.logging.Logger
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.springframework.boot.gradle.tasks.run.BootRun
import java.io.File
import java.io.IOException

//REACTJS
val frontendDir = layout.projectDirectory.dir("frontend")

val npmInstall by tasks.registering(Exec::class) {
    workingDir = frontendDir.asFile
    commandLine("npm", "ci")

    inputs.files(
        frontendDir.file("package.json"),
        frontendDir.file("package-lock.json"),
    )
    // Marker written by npm; avoids snapshotting the whole node_modules tree.
    outputs.file(frontendDir.file("node_modules/.package-lock.json"))
}

val npmBuild by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = frontendDir.asFile
    commandLine("npm", "run", "build")

    inputs.dir(frontendDir.dir("src"))
    inputs.dir(frontendDir.dir("public"))
    inputs.files(
        frontendDir.file("index.html"),
        frontendDir.file("package.json"),
        frontendDir.file("vite.config.js"),
    )
    outputs.dir(frontendDir.dir("dist"))
}




plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "ir.linuxian"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {

	// Embedded TDchatBot sources live under src/main/java/ir/linuxian/ with
	// its TDLib binary in libs/ + native/ (no external project reference).
	implementation(files("libs/tdlib.jar"))
	implementation("com.google.code.gson:gson:2.10.1")

	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly ("org.postgresql:postgresql")
	implementation("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
	compileOnly("org.projectlombok:lombok:1.18.48")
	annotationProcessor("org.projectlombok:lombok:1.18.48")

	testCompileOnly("org.projectlombok:lombok:1.18.48")
	testAnnotationProcessor("org.projectlombok:lombok:1.18.48")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-test")
	implementation("org.springframework.boot:spring-boot-starter-mail")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("org.springframework.boot:spring-boot-starter-data-rest")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// REMOVE this line - it's causing the problem:
	// implementation("org.hibernate.orm:hibernate-models:7.2.12.Final")
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("-Djava.library.path=${file("native").absolutePath}")
}

tasks.processResources {
    dependsOn(npmBuild)

    from(frontendDir.dir("dist")) {
        into("static")
    }
}

// ---------------------------------------------------------------------------
// Dev mode: `./gradlew bootRun` (or `./gradlew dev`) also starts the Vite dev
// server on http://localhost:5173. Vite proxies /api to Spring Boot on :8080
// (see frontend/vite.config.js), so open :5173 while developing.
//
// Skip the frontend process with `-PnoFrontendDev` (CI, or when you already
// run `npm run dev` yourself).
// ---------------------------------------------------------------------------
val vitePidFile = layout.buildDirectory.file("vite-dev.pid").get().asFile

/**
 * Owns the Vite dev server for one build. Gradle closes build services when the
 * build ends (including cancellation), which is where the process is killed.
 */
abstract class ViteDevService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private var process: Process? = null
    private var pidFile: File? = null

    fun start(frontendDir: File, pidFile: File, log: Logger) {
        if (process?.isAlive == true) return
        stopStale(pidFile)

        log.lifecycle("Starting Vite dev server at http://localhost:5173 ...")
        val started = try {
            ProcessBuilder("npm", "run", "dev")
                .directory(frontendDir)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            throw GradleException("Could not start 'npm run dev'. Is npm installed and on PATH?", e)
        }

        process = started
        this.pidFile = pidFile
        pidFile.parentFile.mkdirs()
        pidFile.writeText(started.pid().toString())

        Thread({
            try {
                started.inputStream.bufferedReader().forEachLine { log.lifecycle("[vite] $it") }
            } catch (_: IOException) {
                // Stream closed while shutting down.
            }
        }, "vite-dev-log").apply { isDaemon = true }.start()
    }

    fun stop() {
        val running = process ?: return
        process = null
        pidFile?.delete()
        pidFile = null
        if (running.isAlive) killProcessTree(running.toHandle())
    }

    /** Kills a server left behind by a previous (interrupted) build. */
    fun stopStale(pidFile: File): Boolean {
        val pid = pidFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
        pidFile.delete()
        if (pid == null) return false

        val handle = ProcessHandle.of(pid).orElse(null) ?: return false
        if (!handle.isAlive) return false

        // Guard against PID reuse: only kill a process that still looks like npm.
        if (!handle.info().commandLine().orElse("").contains("npm")) return false

        killProcessTree(handle)
        return true
    }

    override fun close() = stop()

    private fun killProcessTree(handle: ProcessHandle) {
        val children = handle.descendants().toList()
        handle.destroy()
        children.forEach { it.destroy() }
        repeat(20) {
            if (!handle.isAlive && children.none { it.isAlive }) return
            Thread.sleep(100)
        }
        children.filter { it.isAlive }.forEach { it.destroyForcibly() }
        if (handle.isAlive) handle.destroyForcibly()
    }
}

val viteDevService = gradle.sharedServices.registerIfAbsent("viteDev", ViteDevService::class) {}

val skipFrontendDev = providers.gradleProperty("noFrontendDev")

tasks.named<BootRun>("bootRun") {
    usesService(viteDevService)
    doFirst {
        if (!skipFrontendDev.isPresent) {
            viteDevService.get().start(frontendDir.asFile, vitePidFile, logger)
        }
    }
    doLast { viteDevService.get().stop() }
}

tasks.register("dev") {
    group = "application"
    description = "Runs Spring Boot and the Vite dev server (http://localhost:5173) together."
    dependsOn("bootRun")
}

tasks.register("stopFrontend") {
    group = "application"
    description = "Stops a Vite dev server left behind by an interrupted bootRun."
    usesService(viteDevService)
    doLast {
        if (viteDevService.get().stopStale(vitePidFile)) logger.lifecycle("Stopped the Vite dev server.")
        else logger.lifecycle("No Vite dev server was running.")
    }
}

// TDLib native library is embedded at <project>/native (libtdjni.so). The JVM
// must find it via java.library.path when second runs (bootRun, tests, ...).
// TelegramService also has a System.load() fallback for `java -jar` runs,
// but passing -Djava.library.path explicitly is the reliable path.
val tdNativeDir = file("native").absolutePath
tasks.withType<JavaExec>().configureEach {
	jvmArgs("-Djava.library.path=$tdNativeDir")
}
