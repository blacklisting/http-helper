plugins {
    kotlin("jvm") version "1.8.21"
}

repositories {
    mavenCentral()
    maven {
        name = "LocalM2"
    }
}

dependencies {
    implementation("com.blacklisting", "lib", "0.0.0.1")
}
