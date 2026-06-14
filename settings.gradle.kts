pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // LittleSkin / Authlib-Injector 相关 Maven 仓库
        maven("https://libraries.minecraft.net")
    }
}

rootProject.name = "YCL-Launcher"
include(":app")