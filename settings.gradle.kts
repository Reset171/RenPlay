pluginManagement { 
    repositories { 
        google()
        mavenCentral()
        gradlePluginPortal() 
    } 
}
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val ghUser = localProperties.getProperty("github.user") ?: System.getenv("GH_USERNAME") ?: ""
val ghToken = localProperties.getProperty("github.token") ?: System.getenv("GH_TOKEN") ?: ""

dependencyResolutionManagement { 
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { 
        google()
        mavenCentral() 
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
            credentials {
                username = ghUser
                password = ghToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
            credentials {
                username = ghUser
                password = ghToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/oneui-design")
            credentials {
                username = ghUser
                password = ghToken
            }
        }
    } 
}

rootProject.name = "RenPlay"
include(
  ":app"
)
