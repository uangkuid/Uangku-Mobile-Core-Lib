plugins {
    id("com.vanniktech.maven.publish")
}

// Tidak perlu blok mavenPublishing manual: karena SONATYPE_HOST dan
// RELEASE_SIGNING_ENABLED di-set di gradle.properties, plugin
// com.vanniktech.maven.publish OTOMATIS mengaktifkan publishToMavenCentral()
// + signAllPublications(), serta meresolve koordinat dan POM dari:
// - GROUP & VERSION_NAME di root gradle.properties
// - POM_ARTIFACT_ID & POM_NAME di gradle.properties tiap module
// - POM_* lainnya di root gradle.properties
// Memanggilnya manual di sini justru error ("property is final").
