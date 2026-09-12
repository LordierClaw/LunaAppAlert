package dev.lordierclaw.lunaappalert.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.lordierclaw.lunaappalert.core.OwnerType

enum class AccessAction { USAGE, OVERLAY, NOTIFICATIONS, BATTERY }

@Composable
fun AppAlertApp(vm: AppViewModel, openAccess: (AccessAction) -> Unit) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { notice ->
            val result = snackbar.showSnackbar(notice.text, actionLabel = if (notice.undo != null) "Hoàn tác" else null,
                withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) notice.undo?.let(vm::undo)
        }
    }
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else NavHost(navController = nav, startDestination = if (settings.onboardingCompleted) "home" else "welcome") {
            composable("welcome") { WelcomeScreen { nav.navigate("access") } }
            composable("access") {
                AccessScreen(vm, { nav.popBackStack() }, openAccess) {
                    if (settings.onboardingCompleted) nav.popBackStack()
                    else vm.finishOnboarding { nav.navigate("home") { popUpTo("welcome") { inclusive = true } } }
                }
            }
            composable("home") { HomeScreen(vm,
                onSettings = { nav.navigate("settings") }, onAccess = { nav.navigate("access") },
                onApp = { nav.navigate("app/$it") }, onGroup = { nav.navigate("group/$it") },
                onAdd = { nav.navigate("picker/none") }, onCreate = { nav.navigate("group-edit/new") }) }
            composable("settings") { SettingsScreen(vm, { nav.popBackStack() }, openAccess) }
            composable("picker/{groupId}") { entry ->
                AppPickerScreen(vm, entry.arguments?.getString("groupId")?.takeUnless { it == "none" },
                    onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
            }
            composable("group-edit/{id}") { entry ->
                GroupEditorScreen(vm, entry.arguments?.getString("id")?.takeUnless { it == "new" },
                    onBack = { nav.popBackStack() }, onDone = { id ->
                        nav.popBackStack()
                        if (entry.arguments?.getString("id") == "new") nav.navigate("group/$id")
                    })
            }
            composable("group/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                GroupScreen(vm, id, { nav.popBackStack() }, { nav.navigate("group-edit/$id") },
                    { nav.navigate("picker/$id") }, { nav.navigate("app/$it") },
                    { nav.navigate("rule/GROUP/$id/${it ?: "new"}") })
            }
            composable("app/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                AppDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate("group/$it") },
                    { nav.navigate("rule/APP/$id/${it ?: "new"}") })
            }
            composable("rule/{type}/{ownerId}/{ruleId}") { entry ->
                val type = OwnerType.valueOf(entry.arguments?.getString("type") ?: "APP")
                val owner = entry.arguments?.getString("ownerId").orEmpty()
                RuleEditorScreen(vm, type, owner, entry.arguments?.getString("ruleId")?.takeUnless { it == "new" },
                    onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() },
                    onExisting = { id -> nav.navigate("rule/${type.name}/$owner/$id") { popUpTo(entry.destination.id) { inclusive = true } } },
                    openAccess = openAccess)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
    }
}
