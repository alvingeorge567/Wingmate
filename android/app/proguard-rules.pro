# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.wingmate.keyboard.net.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wingmate.keyboard.net.**$$serializer { *; }
