# SkyPet minimal ProGuard rules (release build only; isMinifyEnabled = false currently).
# Keep the launcher activity and the overlay service entry points.
-keep class com.skypet.pet.MainActivity { *; }
-keep class com.skypet.pet.overlay.PetOverlayService { *; }
