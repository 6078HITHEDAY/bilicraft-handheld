pluginManagement {
    repositories {
        // 国内镜像优先（命中即走加速通道）；CI（GitHub Actions）访问阿里云会 502，跳过
        if (System.getenv("CI") == null) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google")
        }
        // 官方源兜底（镜像未命中时回落）
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像优先（命中即走加速通道）；CI 跳过（阿里云对海外 runner 返回 502）
        if (System.getenv("CI") == null) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        // 官方源兜底（镜像未命中时回落）
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "BilicraftHandheld"
include(":plugin-api")
include(":app")