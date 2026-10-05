# MediaPipe (efek wajah) memanggil kelas Java lewat JNI & refleksi: jangan diubah R8.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# RootEncoder hanya diperlukan bila aplikasi memakai ZinEffects (aplikasi menyertakannya sendiri).
-dontwarn com.pedro.**
