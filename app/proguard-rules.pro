# kotlinx.serialization — сохраняем сериализаторы.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ru.sapn.vpn.data.remote.dto.** {
    *** Companion;
}
-keepclassmembers class ru.sapn.vpn.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers,allowshrinking,allowobfuscation interface retrofit2.* { @retrofit2.http.* <methods>; }
