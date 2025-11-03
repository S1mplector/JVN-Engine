plugins {
  `java-library`
}

sourceSets {
  main {
    java {
      exclude("**/simp3/Simp3/**")
      // keep legacy exclusions; include our adapter
      exclude("**/audio/simp3/**")
    }
  }
}

dependencies {
  api(project(":core"))
  // Audio-engine published to mavenLocal via `mvn -f audio-engine/pom.xml install`
  implementation("com.musicplayer:simp3:1.0.0")
}
