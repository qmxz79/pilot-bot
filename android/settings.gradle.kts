pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://maven.amap.com/repository/maven-public/")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://maven.amap.com/repository/maven-public/")
    }
}

rootProject.name = "Pilot-bot"
include(":app")
