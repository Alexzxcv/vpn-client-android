# kotlinx.serialization — сохраняем сериализаторы. DTO лежат прямо в
# ru.sapn.vpn.data.remote (Dto.kt, GitHubDto.kt) — не в подпакете .dto.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ru.sapn.vpn.data.remote.** {
    *** Companion;
}
-keepclassmembers class ru.sapn.vpn.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers,allowshrinking,allowobfuscation interface retrofit2.* { @retrofit2.http.* <methods>; }

# net.i2p.crypto:eddsa (Ed25519-идентичность устройства) ссылается на JDK-классы
# sun.security.*, которых нет на Android — глушим предупреждения R8 и сохраняем
# саму библиотеку целиком (используется для подписи запросов /vpn/config).
-dontwarn sun.security.x509.**
-dontwarn sun.security.provider.**
-keep class net.i2p.crypto.eddsa.** { *; }
