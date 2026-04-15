package com.saarlabs.tminus.ui

import android.util.Log
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
import com.saarlabs.tminus.commute.CommuteProfile
import com.saarlabs.tminus.commute.CommuteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
public fun CommuteEditorRoute(
    navController: NavController,
    profileId: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CommuteRepository(context.applicationContext) }
    var initial by remember { mutableStateOf<CommuteProfile?>(null) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(profileId) {
        initial =
            if (profileId == "new") {
                null
            } else {
                repo.loadProfiles().find { it.id == profileId }
            }
        ready = true
    }

    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    CommuteEditorScreen(
        initial = initial,
        onSave = { profile ->
            scope.launch {
                try {
                    val list = repo.loadProfiles().toMutableList()
                    val idx = list.indexOfFirst { it.id == profile.id }
                    if (idx >= 0) {
                        list[idx] = profile
                    } else {
                        list.add(profile)
                    }
                    repo.saveProfiles(list)
                    runCatching { navController.popBackStack() }
                        .onFailure { Log.e(TAG, "popBackStack failed after save", it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save commute", e)
                }
            }
        },
        onCancel = { navController.popBackStack() },
    )
}

private const val TAG = "CommuteEditorRoute"
