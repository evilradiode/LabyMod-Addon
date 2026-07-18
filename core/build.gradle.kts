import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

dependencies {
    labyProcessor()
    api(project(":api"))

    // JLayer für MP3-Stream-Unterstützung
    addonMavenDependency("javazoom:jlayer:1.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-proc:none")
}

tasks.test {
    useJUnitPlatform()
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.DEFAULT
}