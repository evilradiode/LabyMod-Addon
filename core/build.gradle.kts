import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

dependencies {
    labyProcessor()
    api(project(":api"))

    // JLayer für MP3-Stream-Unterstützung
    addonMavenDependency("javazoom:jlayer:1.0.1")
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.DEFAULT
}