# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
# Keep cloud data classes for JSON serialization (CloudConnection, CloudFile)
-keepclassmembers class com.filecleaner.app.data.cloud.CloudConnection { *; }
-keepclassmembers class com.filecleaner.app.data.cloud.CloudFile { *; }
-keepclassmembers class com.filecleaner.app.data.cloud.CloudProvider { *; }
-keepclassmembers class com.filecleaner.app.data.cloud.ProviderType { *; }
# Keep FileItem for Parcelable
-keepclassmembers class com.filecleaner.app.data.FileItem { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# JSch (SFTP library)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
