# Gson — keep app models (serialized via com.google.gson reflectively)
-keep class com.nexoratech.markets.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
