plugins {
    alias(libs.plugins.kotlinJvm)
    application
}
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_26
    targetCompatibility = JavaVersion.VERSION_26
}
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
dependencies {
    implementation(libs.coroutinesCore)
    testImplementation(libs.coroutinesTest)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
application { mainClass.set("staff.android.async.MainKt") }
tasks.test {
    useJUnitPlatform { excludeTags("exercise") }
    testLogging { events("passed", "skipped", "failed") }
}
tasks.register<Test>("exerciseTest") {
    description = "Runs the intentionally defective independent image-slot starter."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("exercise") }
    testLogging { events("passed", "skipped", "failed") }
    shouldRunAfter(tasks.test)
}
