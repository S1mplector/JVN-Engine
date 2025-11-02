plugins {
  `java-library`
  application
  id("org.openjfx.javafxplugin") version "0.0.13"
}

dependencies {
  api(project(":core"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
  implementation(project(":audio-integration"))
}

application {
  mainClass.set("com.jvn.billiards.app.BilliardsGameApp")
}

javafx {
  version = "21.0.3"
  modules("javafx.controls", "javafx.graphics", "javafx.media", "javafx.swing")
}
