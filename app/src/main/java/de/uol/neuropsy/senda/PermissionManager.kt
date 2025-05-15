// PermissionManager.kt
package de.uol.neuropsy.senda

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PermissionManager(caller: ActivityResultCaller) {
    // This holds the callback from the launcher
    private var permissionContinuation:
            ( (Map<String, Boolean>) -> Unit )? = null

    // Register the launcher exactly once, in init
    private val launcher: ActivityResultLauncher<Array<String>> =
        caller.registerForActivityResult(RequestMultiplePermissions()) { result ->
            // Resume the suspended coroutine with the result
            permissionContinuation?.let { cont ->
                cont(result)
                permissionContinuation = null
            }
        }

    /**
     * Suspend until the user has granted or denied the given permissions.
     * Must be called *after* this PermissionManager is constructed (e.g. in onCreate).
     */
    suspend fun requestPermissions(vararg permissions: String): Map<String, Boolean> =
        suspendCancellableCoroutine { cont ->
            // If another request is in-flight, cancel it
            if (permissionContinuation != null) {
                cont.resume(emptyMap())  // or handle as error
                return@suspendCancellableCoroutine
            }
            // Save the continuation so the launcher callback can resume it
            permissionContinuation = { result ->
                if (cont.isActive) cont.resume(result)
            }
            // Launch the permission dialog
            launcher.launch(permissions as Array<String>)
            // If the coroutine is cancelled, clean up
            cont.invokeOnCancellation {
                permissionContinuation = null
            }
        }
}
