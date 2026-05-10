package com.beto.app.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.contacts.ContactRepository
import com.beto.app.ui.BetoTheme
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Activity del Modo Compañero. Transparente sobre la app actual; el Compose
 * `ModalBottomSheet` ocupa el 75-90% inferior.
 */
class CompanionActivity : ComponentActivity() {

    private val busScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(LogTags.LLM).i("CompanionActivity opened")
        busScope.launch { AgentBus.emit(AgentEvent.CompanionOpened) }

        setContent {
            val viewModel = remember { buildViewModel() }
            val contactRepository = remember { ContactRepository(applicationContext) }
            BetoTheme(darkTheme = true) {
                CompanionSheet(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                    contactRepository = contactRepository,
                )
            }

            LaunchedEffect(Unit) {
                Timber.tag(LogTags.LLM).d("Companion sheet attached")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(LogTags.LLM).i("CompanionActivity closed")
        // Emit en un scope global porque busScope se cancela junto con la Activity.
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            AgentBus.emit(AgentEvent.CompanionClosed)
        }
        busScope.cancel()
    }

    private fun buildViewModel(): CompanionViewModel = CompanionViewModel()
}
