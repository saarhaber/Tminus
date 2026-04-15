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
import com.saarlabs.tminus.features.LastTrainProfile
import com.saarlabs.tminus.features.LastTrainRepository
import kotlinx.coroutines.launch

@Composable
public fun LastTrainEditorRoute(navController: NavController, profileId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { LastTrainRepository(context.applicationContext) }
    var initial by remember { mutableStateOf<LastTrainProfile?>(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        initial =
            if (profileId == "new") null
            else repo.load().find { it.id == profileId }
        ready = true
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LastTrainEditorScreen(
        initial = initial,
        onSave = { profile ->
            scope.launch {
                val list = repo.load().toMutableList()
                val i = list.indexOfFirst { it.id == profile.id }
                if (i >= 0) list[i] = profile else list.add(profile)
                repo.save(list)
                navController.popBackStack()
            }
        },
        onCancel = { navController.popBackStack() },
    )
}
