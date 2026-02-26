# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
# Keep data classes
-keepclassmembers class com.filecleaner.app.data.** { *; }
