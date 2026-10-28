package br.com.nexus.assistant

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexus.assistant.data.local.LocalModelProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** CORE package and actual ViewModel flow; no model, private user data or network fixture is installed. */
@RunWith(AndroidJUnit4::class)
class CoreOptionalModelsTest {
    @Test(timeout = 30_000L)
    fun coreOmitsGenerativeWeightsAndDoesNotStartADownloadWhenChatPreparationIsRequested() = runBlocking<Unit> {
        assumeFalse("The optional bundled development variant has a separate contract", BuildConfig.BUNDLED_MODEL)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        assertTrue("CORE must not carry either large GGUF", application.assets.list("models")
            .orEmpty().none { it.endsWith(".gguf", ignoreCase = true) })
        lateinit var store: ViewModelStore
        lateinit var model: NexusViewModel
        instrumentation.runOnMainSync {
            store = ViewModelStore()
            model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[NexusViewModel::class.java]
        }
        try {
            assertEquals(LocalModelProfile.entries.toSet(), model.localModelStates.value.keys)
            instrumentation.runOnMainSync { model.prepareLocalAi() }
            delay(500)
            assertFalse("Opening CORE chat must never authorize a model transfer", model.localModelDownloadActive.value)
            assertNull(model.localModelOperationProfile.value)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
