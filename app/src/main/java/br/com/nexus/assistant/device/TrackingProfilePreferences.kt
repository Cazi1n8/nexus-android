package br.com.nexus.assistant.device

import android.content.Context

/** Small local setting; it contains no device identifiers or user content. */
object TrackingProfilePreferences {
    private const val PREFERENCES = "nexus_tracking"
    private const val PROFILE = "performance_profile"

    fun read(context: Context): TrackingProfile = TrackingProfile.fromStored(
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(PROFILE, null),
    )

    fun write(context: Context, profile: TrackingProfile) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(PROFILE, profile.name).apply()
    }
}
