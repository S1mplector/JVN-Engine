plugins {
  `java-library`
  application
  id("org.openjfx.javafxplugin") version "0.0.13"
}

repositories {
  mavenLocal()
  mavenCentral()
}

val jvnGroup = (findProperty("jvnGroup") as String?) ?: "com.jvn"
val jvnVersion = (findProperty("jvnVersion") as String?) ?: "0.1-SNAPSHOT"

dependencies {
  api("${jvnGroup}:core:${jvnVersion}")
  implementation("${jvnGroup}:fx:${jvnVersion}")
  implementation("${jvnGroup}:scripting:${jvnVersion}")
  implementation("${jvnGroup}:audio-integration:${jvnVersion}")
}

application {
  mainClass.set("com.jvn.billiards.app.BilliardsGameApp")
}

javafx {
  version = "21.0.3"
  modules("javafx.controls", "javafx.graphics", "javafx.media", "javafx.swing")
}
