plugins {
  `java-library`
}

sourceSets {
  main {
    java {
      exclude("**/simp3/Simp3/**")
      // keep legacy exclusions
    }
  }
}

dependencies {
  api(project(":core"))
  // Audio-engine published to mavenLocal via `mvn -f audio-engine/pom.xml install`
  implementation("com.musicplayer:simp3:1.0.0")
}
