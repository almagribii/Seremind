package com.almagribii.seremind.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.almagribii.seremind.MainActivity
import com.almagribii.seremind.ui.navigation.Screen
import com.almagribii.seremind.ui.theme.SeremindTheme

private class CustomLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInstance = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInstance
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStoreInstance.clear()
    }
}

object OverlayWindowManager {
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: CustomLifecycleOwner? = null

    fun showOverlay(
        context: Context,
        packageName: String,
        appName: String,
        delaySeconds: Int
    ) {
        runOnMain {
            showOverlayInternal(context, packageName, appName, delaySeconds)
        }
    }

    fun dismissOverlay(context: Context) {
        runOnMain { dismissOverlayInternal(context) }
    }

    @SuppressLint("InflateParams")
    private fun showOverlayInternal(
        context: Context,
        packageName: String,
        appName: String,
        delaySeconds: Int
    ) {
        dismissOverlayInternal(context)

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val owner = CustomLifecycleOwner()
        val view = ComposeView(appContext)

        // Set owners before attaching the view. setContent is deliberately called
        // after addView so Compose resolves the owners from the attached tree.
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        wm.addView(view, params)

        windowManager = wm
        overlayView = view
        lifecycleOwner = owner
        view.setContent {
            SeremindTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0f)
                ) {
                    GateOverlay(
                        packageName = packageName,
                        appName = appName,
                        delaySeconds = delaySeconds,
                        onCancel = {
                            dismissOverlay(appContext)
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(homeIntent)
                        },
                        onOpenAllowed = { dismissOverlay(appContext) },
                        onWriteReflection = { prompt ->
                            dismissOverlay(appContext)
                            val intent = Intent(appContext, MainActivity::class.java).apply {
                                putExtra("navigate_to", Screen.JournalEditor.createRoute(prompt))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            appContext.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    private fun dismissOverlayInternal(context: Context) {
        val view = overlayView ?: return
        val wm = windowManager
            ?: context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            try {
                wm.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The view was already removed by the system.
            }
        }
        overlayView = null
        windowManager = null
        lifecycleOwner?.let {
            it.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            it.destroy()
        }
        lifecycleOwner = null
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}
