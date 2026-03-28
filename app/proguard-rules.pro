-keep class com.localllm.llama.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * {
    @com.google.dagger.** *;
}
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class com.localllm.app.**$$serializer { *; }
-keepclassmembers class com.localllm.app.** {
    *** Companion;
}
