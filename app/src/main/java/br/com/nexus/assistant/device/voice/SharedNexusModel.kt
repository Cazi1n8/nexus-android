package br.com.nexus.assistant.device.voice

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import br.com.nexus.assistant.NexusViewModel

/** One application-only model owner for UI and explicitly started voice sessions.
 * Never stores an Activity. A service does not create a second local LLM engine.
 */
object SharedNexusModel {
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    @Volatile var uiVisible: Boolean = false
        private set

    @MainThread
    fun get(context: Context): NexusViewModel {
        check(Looper.myLooper() == Looper.getMainLooper()) { "O assistente compartilhado deve ser obtido na thread principal." }
        val application = context.applicationContext as Application
        return ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[NexusViewModel::class.java]
    }

    @MainThread fun onUiStarted() { uiVisible = true }
    @MainThread fun onUiStopped() { uiVisible = false }
}
