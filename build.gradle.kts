// Versoes fixadas de proposito: AGP 8.13.2 + Gradle 8.13 + Kotlin 2.2.20 e uma
// combinacao estavel e conhecida. O AGP 9.x existe, mas e novo demais para um
// projeto que precisa compilar de primeira e ser mantido por uma pessoa so.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}
