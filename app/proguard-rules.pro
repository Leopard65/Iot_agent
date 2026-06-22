# ===========================================================================
# lot — ProGuard rules for release builds
# ===========================================================================

# --- General ----------------------------------------------------------------

# Keep line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations used by Room, OkHttp and Compose
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Parcelable / Serializable (future-proofing)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Kotlin ------------------------------------------------------------------

-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- OkHttp ------------------------------------------------------------------

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# --- Room --------------------------------------------------------------------

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
}

# --- DataStore ---------------------------------------------------------------

-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# --- Jetpack Compose ---------------------------------------------------------

-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# --- Coil --------------------------------------------------------------------

-dontwarn coil.**
-keep class coil.** { *; }

# --- PDFBox Android (com.tom-roush:pdfbox-android) --------------------------

-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn java.beans.**
-dontwarn com.sun.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.pdmodel.font.PDType1Font
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# --- AndroidX Lifecycle / ViewModel ------------------------------------------

-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# --- AndroidX Navigation -----------------------------------------------------

-keep class androidx.navigation.** { *; }

# --- WorkManager -------------------------------------------------------------

-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# --- Enum safety -------------------------------------------------------------

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- R8 compatibility --------------------------------------------------------

-keep class * implements com.google.firebase.components.ComponentRegistrar
