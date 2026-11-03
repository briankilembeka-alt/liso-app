# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class ug.liso.app.data.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ug.liso.app.data.**$$serializer { *; }
