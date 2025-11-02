plugins {
  `java-library`
  application
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
