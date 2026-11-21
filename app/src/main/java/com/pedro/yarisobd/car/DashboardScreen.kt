package com.pedro.yarisobd.car

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pedro.yarisobd.AppPrefs
import com.pedro.yarisobd.R
import com.pedro.yarisobd.render.DashboardRenderer
import com.pedro.yarisobd.obd.ConnectionController
import com.pedro.yarisobd.telemetry.DataSource
import com.pedro.yarisobd.telemetry.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tela principal no carro.
 *
 * Estratégia de exibição: em vez de tentar encaixar gauges nos componentes de
 * template (que são listas, textos e ícones, sem Canvas), o app usa o único
 * mecanismo documentado que libera desenho livre — a `Surface` entregue pelo
 * host para templates de navegação. O painel inteiro é desenhado nela.
 *
 * Referência: https://developer.android.com/training/cars/apps/library/draw-maps
 */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private val renderer = DashboardRenderer(carContext, AppPrefs.dashboardStyle(carContext))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastStyleCheckMs = 0L

    @Volatile
    private var surfaceContainer: SurfaceContainer? = null

    @Volatile
    private var errorMessage: String? = null

    private var visibleArea: Rect? = null
    private var renderJob: Job? = null
    private var framesDrawn = 0L
    private var fpsWindowStartMs = 0L
    private var fpsWindowFrames = 0L

    @Volatile
    private var measuredFps = 0f

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            Log.i(TAG, "onSurfaceAvailable ${surfaceContainer.width}x${surfaceContainer.height} @${surfaceContainer.dpi}dpi")
            this@DashboardScreen.surfaceContainer = surfaceContainer
            startRendering()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            this@DashboardScreen.visibleArea = visibleArea
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            // Mantido vazio: o painel desenha a tela inteira e usa apenas
            // visibleArea para não colocar números embaixo do chrome do host.
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            Log.i(TAG, "onSurfaceDestroyed")
            stopRendering()
            this@DashboardScreen.surfaceContainer = null
        }
    }

    init {
        val appManager = carContext.getCarService(AppManager::class.java)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        appManager.setSurfaceCallback(surfaceCallback)
                        // Nesta fase, sobe o adaptador virtual automaticamente
                        // (configurável) para permitir testar na central sem
                        // tocar no celular. O painel avisa que os dados são
                        // simulados.
                        if (AppPrefs.autoStartSim(carContext)) {
                            ConnectionController.startIfIdle(carContext, auto = true)
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        appManager.setSurfaceCallback(null)
                        stopRendering()
                        ConnectionController.stopIfAutoStarted()
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        stopRendering()
                        scope.cancel()
                    }
                    else -> Unit
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        return try {
            NavigationTemplate.Builder()
                .setActionStrip(
                    // Um único ícone discreto: o painel deve ser o protagonista.
                    ActionStrip.Builder()
                        .addAction(
                            Action.Builder()
                                .setTitle(carContext.getString(R.string.screen_menu))
                                .setIcon(
                                    CarIcon.Builder(
                                        IconCompat.createWithResource(carContext, R.drawable.ic_more),
                                    ).build(),
                                )
                                .setOnClickListener(
                                    OnClickListener {
                                        screenManager.push(
                                            MenuScreen(carContext) {
                                                screenManager.push(DiagnosticsScreen(carContext, ::status))
                                            },
                                        )
                                    },
                                )
                                .build(),
                        )
                        .build(),
                )
                .build()
        } catch (error: Exception) {
            // Se o host/app combinação não aceitar o template de navegação,
            // a tela cai para um template simples — melhor mostrar estado do
            // que a tela preta.
            errorMessage = "NavigationTemplate: ${error.message}"
            Log.e(TAG, "Falha ao montar NavigationTemplate", error)
            PaneTemplate.Builder(
                Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setTitle("Painel indisponível")
                            .addText(error.message ?: "erro desconhecido")
                            .build(),
                    )
                    .build(),
            ).setTitle(carContext.getString(R.string.screen_dashboard))
                .build()
        }
    }

    fun status(): ScreenStatus {
        val container = surfaceContainer
        val data = TelemetryRepository.state.value
        val now = System.currentTimeMillis()
        val ageMs = if (data.updatedAtMs == 0L) -1L else now - data.updatedAtMs

        return ScreenStatus(
            surfaceState = if (container?.surface?.isValid == true) "ativa" else "ausente",
            surfaceSize = container?.let { "${it.width}x${it.height} @${it.dpi}dpi" } ?: "—",
            framesDrawn = framesDrawn,
            measuredFps = measuredFps,
            dataSource = "Estilo: ${renderer.style.label} · " + when (data.source) {
                DataSource.REAL -> "OBD-II (scanner físico)"
                DataSource.EMULATED -> "Adaptador virtual (ELM327 emulado)"
                DataSource.DEMO -> "Simulação interna (sem protocolo)"
                DataSource.NONE -> "sem fonte"
            },
            dataAge = if (ageMs < 0) "sem leitura" else "$ageMs ms",
            lastError = errorMessage,
        )
    }

    private fun startRendering() {
        if (renderJob?.isActive == true) return
        renderJob = scope.launch {
            while (isActive) {
                drawFrame()
                delay(FRAME_INTERVAL_MS)
            }
        }
    }

    private fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
    }

    private fun drawFrame() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return

        val now = System.currentTimeMillis()
        if (fpsWindowStartMs == 0L) fpsWindowStartMs = now

        // Reaplica a preferência de estilo uma vez por segundo: permite trocar o
        // visual pelo celular com a tela do carro já aberta, sem reconectar.
        if (now - lastStyleCheckMs > STYLE_CHECK_INTERVAL_MS) {
            lastStyleCheckMs = now
            val preferred = AppPrefs.dashboardStyle(carContext)
            if (preferred != renderer.style) renderer.style = preferred
        }

        val canvas = try {
            surface.lockCanvas(null)
        } catch (error: Exception) {
            errorMessage = "lockCanvas: ${error.message}"
            Log.w(TAG, "lockCanvas falhou", error)
            return
        }

        try {
            renderer.draw(
                canvas = canvas,
                width = container.width,
                height = container.height,
                data = TelemetryRepository.state.value,
                nowMs = now,
                fps = measuredFps,
            )
            framesDrawn++
            fpsWindowFrames++
        } catch (error: Exception) {
            errorMessage = "draw: ${error.message}"
            Log.w(TAG, "Falha ao desenhar quadro", error)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (error: Exception) {
                errorMessage = "unlockCanvasAndPost: ${error.message}"
            }
        }

        val windowMs = now - fpsWindowStartMs
        if (windowMs >= FPS_WINDOW_MS) {
            measuredFps = fpsWindowFrames * 1000f / windowMs
            fpsWindowStartMs = now
            fpsWindowFrames = 0
        }
    }

    companion object {
        private const val TAG = "ObdDashboard"

        /**
         * ~12 quadros por segundo. Um painel automotivo não ganha nada acima
         * disso, e cada quadro em ELM327 custa CPU e bateria sem retorno
         * visual. O gargalo real é o adaptador (10-20 comandos/s), não o desenho.
         */
        private const val FRAME_INTERVAL_MS = 80L
        private const val FPS_WINDOW_MS = 1000L
        private const val STYLE_CHECK_INTERVAL_MS = 1000L
    }
}


