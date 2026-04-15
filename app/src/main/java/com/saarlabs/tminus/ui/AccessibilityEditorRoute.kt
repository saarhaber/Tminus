package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.saarlabs.tminus.features.AccessibilityRepository
import com.saarlabs.tminus.features.AccessibilityWatch
import kotlinx.coroutines.launch

@Composable
public fun AccessibilityEditorRoute(navController: NavController, id: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AccessibilityRepository(context.applicationContext) }
    var initial by remember { mutableStateOf<AccessibilityWatch?>(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        initial = if (id == "new") null else repo.load().find { it.id == id }
        ready = true
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    AccessibilityEditorScreen(
        initial = initial,
        onSave = { w ->
            scope.launch {
                val list = repo.load().toMutableList()
                val i = list.indexOfFirst { it.id == w.id }
                if (i >= 0) list[i] = w else list.add(w)
                repo.save(list)
                navController.popBackStack()
            }
        },
        onCancel = { navController.popBackStack() },
    )
}
