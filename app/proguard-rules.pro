# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Data Models for reflection and JSON parsing (Moshi / Serialization)
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }

# Every model in com.example.data.model round-trips Firestore data through
# hand-written fromFirestoreMap/toFirestoreMap functions that store enums as
# their .name string and decode them back via `SomeEnum.valueOf(str)` — R8's
# default optimization can otherwise rewrite a small enum into plain ints,
# which breaks valueOf()/name entirely at runtime with no compile-time
# warning. This is the standard, broadly-recommended R8 safety net for that
# exact pattern, applied to every enum in the app (not just the model
# package, since UserRole/OwnerPackageTier/etc. are also switched on by
# string name elsewhere, e.g. Cloud Functions purpose/role checks).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room Database rules
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# Moshi rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn javax.annotation.**
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Retrofit & OkHttp rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# osmdroid (OpenStreetMap)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
