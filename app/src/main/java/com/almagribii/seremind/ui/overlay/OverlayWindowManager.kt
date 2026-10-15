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
    @Volatile
    var isOverlayVisible: Boolean = false
        private set

    @Volatile
    var isSessionTimerVisible: Boolean = false
        private set

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: CustomLifecycleOwner? = null
    private var timerParams: WindowManager.LayoutParams? = null

    fun showOverlay(
        context: Context,
        packageName: String,
        appName: String,
        delaySeconds: Int,
        maxSessionMinutes: Int = 15,
        cooldownMinutes: Int = 30,
        isCooldownActive: Boolean = false,
        cooldownMinutesLeft: Int = 0,
        cooldownRemainingSeconds: Int = cooldownMinutesLeft * 60,
        isWarningKick: Boolean = false,
        warningMessage: String? = null,
        onStartEmergencySession: (() -> Unit)? = null,
        onStartNormalSession: (() -> Unit)? = null
    ) {
        runOnMain {
            showOverlayInternal(
                context,
                packageName,
                appName,
                delaySeconds,
                maxSessionMinutes,
                cooldownMinutes,
                isCooldownActive,
                cooldownMinutesLeft,
                cooldownRemainingSeconds,
                isWarningKick,
                warningMessage,
                onStartEmergencySession,
                onStartNormalSession
            )
        }
    }

    fun dismissOverlay(context: Context) {
        runOnMain { dismissOverlayInternal(context) }
    }

    fun showSessionTimer(
        context: Context,
        appName: String,
        remainingSeconds: Int,
        totalSeconds: Int,
        onTick: ((Int) -> Unit)? = null,
        onTimerExpired: (() -> Unit)? = null
    ) {
        runOnMain {
            showSessionTimerInternal(context, appName, remainingSeconds, totalSeconds, onTick, onTimerExpired)
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlayInternal(
        context: Context,
        packageName: String,
        appName: String,
        delaySeconds: Int,
        maxSessionMinutes: Int = 15,
        cooldownMinutes: Int = 30,
        isCooldownActive: Boolean = false,
        cooldownMinutesLeft: Int = 0,
        cooldownRemainingSeconds: Int = cooldownMinutesLeft * 60,
        isWarningKick: Boolean = false,
        warningMessage: String? = null,
        onStartEmergencySession: (() -> Unit)? = null,
        onStartNormalSession: (() -> Unit)? = null
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        wm.addView(view, params)

        windowManager = wm
        overlayView = view
        lifecycleOwner = owner
        isOverlayVisible = true
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
                        maxSessionMinutes = maxSessionMinutes,
                        cooldownMinutes = cooldownMinutes,
                        isCooldownActive = isCooldownActive,
                        cooldownMinutesLeft = cooldownMinutesLeft,
                        cooldownRemainingSeconds = cooldownRemainingSeconds,
                        isWarningKick = isWarningKick,
                        warningMessage = warningMessage,
                        onCancel = {
                            dismissOverlay(appContext)
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(homeIntent)
                        },
                        onOpenAllowed = {
                            dismissOverlay(appContext)
                            onStartNormalSession?.invoke()
                        },
                        onEmergencyOpenAllowed = {
                            dismissOverlay(appContext)
                            onStartEmergencySession?.invoke()
                        },
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
        val view = overlayView
        if (view != null) {
            val wm = windowManager
                ?: context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null) {
                try {
                    wm.removeView(view)
                } catch (_: IllegalArgumentException) {
                    // The view was already removed by the system.
                }
            }
        }
        overlayView = null
        windowManager = null
        isOverlayVisible = false
        isSessionTimerVisible = false
        lifecycleOwner?.let {
            it.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            it.destroy()
        }
        lifecycleOwner = null
    }

    fun updateTimerPosition(deltaX: Float, deltaY: Float) {
        runOnMain {
            val p = timerParams ?: return@runOnMain
            val v = overlayView ?: return@runOnMain
            val wm = windowManager ?: return@runOnMain
            p.x = (p.x - deltaX.toInt()).coerceAtLeast(0)
            p.y = (p.y + deltaY.toInt()).coerceAtLeast(0)
            try {
                wm.updateViewLayout(v, p)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showSessionTimerInternal(
        context: Context,
        appName: String,
        remainingSeconds: Int,
        totalSeconds: Int,
        onTick: ((Int) -> Unit)? = null,
        onTimerExpired: (() -> Unit)? = null
    ) {
        dismissOverlayInternal(context)

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val owner = CustomLifecycleOwner()
        val view = ComposeView(appContext)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = appContext.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (60 * density).toInt()
        }

        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        wm.addView(view, params)

        windowManager = wm
        overlayView = view
        lifecycleOwner = owner
        timerParams = params
        isOverlayVisible = false
        isSessionTimerVisible = true
        view.setContent {
            SeremindTheme {
                SessionTimerOverlay(
                    appName = appName,
                    remainingSeconds = remainingSeconds,
                    totalSeconds = totalSeconds,
                    onDrag = { dx, dy -> updateTimerPosition(dx, dy) },
                    onDismiss = { dismissOverlay(appContext) },
                    onTick = { sec -> onTick?.invoke(sec) },
                    onTimerExpired = { onTimerExpired?.invoke() }
                )
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}
