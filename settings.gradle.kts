pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "the-staff-android-engineer"
include(":state-ownership")
project(":state-ownership").projectDir = file("labs/01-state-ownership")
include(":foundations")
project(":foundations").projectDir = file("labs/00-foundations")
include(":api-contracts")
project(":api-contracts").projectDir = file("labs/02-types-api-contracts")
include(":async-ownership")
project(":async-ownership").projectDir = file("labs/03-asynchronous-work")
include(":streams-state")
project(":streams-state").projectDir = file("labs/04-streams-shared-state")
include(":platform-runtime")
project(":platform-runtime").projectDir = file("labs/05-platform-runtime")
