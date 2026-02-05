plugins {
    `java-platform`
    `maven-publish`
}

group = "dev.pikaia.android"
version = project.findProperty("pikaia.sdk.version") as String? ?: "0.1.0-SNAPSHOT"

dependencies {
    constraints {
        api(project(":sdk-core"))
        api(project(":sdk-auth"))
        api(project(":sdk-sync"))
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["javaPlatform"])
            groupId = "dev.pikaia.android"
            artifactId = "sdk-bom"
            version = project.version.toString()
        }
    }
}