# R8 rules for the release build.
#
# Compose, Room and Supabase each ship consumer rules that travel with the
# dependency, so this file only covers what is specific to Wayfare.

# ---------------------------------------------------------------------------
# kotlinx.serialization
#
# Every PostgREST payload is a @Serializable DTO in com.wayfare.app.core. The
# generated `TripDto$$serializer` and its `Companion.serializer()` are looked up
# by name at runtime, so R8 must not rename or drop them. The library's own
# consumer rules cover the general case; these make our wire models explicit,
# because silently losing a serializer shows up as an empty ledger, not a crash.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature

-keepclassmembers class com.wayfare.app.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.wayfare.app.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wayfare.app.core.**$$serializer { *; }

# ---------------------------------------------------------------------------
# Ktor
#
# Engines are discovered through META-INF/services. Keep the service files and
# silence the platform engines Ktor references but we never depend on.
# ---------------------------------------------------------------------------
-keep class io.ktor.client.engine.android.** { *; }
-dontwarn org.slf4j.**
