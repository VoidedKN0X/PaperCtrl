# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.paperctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.paperctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.paperctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
