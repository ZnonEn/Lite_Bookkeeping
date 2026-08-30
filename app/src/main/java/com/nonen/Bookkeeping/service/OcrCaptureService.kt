package com.nonen.Bookkeeping.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.nonen.Bookkeeping.R
import com.nonen.Bookkeeping.data.prefs.SettingsSnapshot
import com.nonen.Bookkeeping.parse.WindowCaptureAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 屏幕识别（OCR）通道：用户授权一次屏幕录制后，本服务持有 MediaProjection。
 * 无障碍通道发现支付页面对其隐藏内容时（微信/支付宝的支付结果页），由此服务
 * 抓取当前屏幕帧，用 ML Kit 中文离线识别出文字，交给与无障碍相同的解析管线入库。
 * 授权在重启后失效，需要重新授权一次。
 */
class OcrCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    val isReady: Boolean
        get() = imageReader != null && virtualDisplay != null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "屏幕识别", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("屏幕识别运行中")
            .setContentText("用于自动记账的支付页面文字识别")
            .setOngoing(true)
            .build()

        // Android 14+ 顺序要求：必须先以前台服务类型 mediaProjection 置前台，
        // 再调 getMediaProjection()，否则系统抛 SecurityException 闪退
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val mediaProjection = getSystemService(MediaProjectionManager::class.java)
            ?.getMediaProjection(Activity.RESULT_OK, resultData)
        if (mediaProjection == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mediaProjection
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, Handler(Looper.getMainLooper()))
        createDisplay()

        instance = this
        OcrEngine.reset()
        return START_NOT_STICKY
    }

    private fun createDisplay() {
        tearDownDisplay()
        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = projection?.createVirtualDisplay(
            "bookkeeping-ocr",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null,
        )
    }

    private fun tearDownDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /** 抓取当前屏幕帧；屏幕尺寸变化（旋转等）时重建虚拟屏并返回 null 等下一轮 */
    fun grabFrame(): Bitmap? {
        val reader = imageReader ?: return null
        val metrics = resources.displayMetrics
        if (metrics.widthPixels != screenWidth || metrics.heightPixels != screenHeight) {
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
            createDisplay()
            return null
        }
        val image = reader.acquireLatestImage() ?: return null
        val bitmap = try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - image.width * 4
            if (rowPadding == 0) {
                Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also {
                    plane.buffer.rewind()
                    it.copyPixelsFromBuffer(plane.buffer)
                }
            } else {
                // 行带对齐填充：先按含填充的宽度拷贝，再裁掉右侧
                val padded = Bitmap.createBitmap(plane.rowStride / 4, image.height, Bitmap.Config.ARGB_8888)
                plane.buffer.rewind()
                padded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                if (cropped != padded) padded.recycle()
                cropped
            }
        } catch (t: Throwable) {
            null
        } finally {
            image.close()
        }
        return bitmap
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        tearDownDisplay()
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: OcrCaptureService? = null
            private set

        const val EXTRA_RESULT = "result_data"
        private const val CHANNEL_ID = "ocr_capture"
        private const val NOTIFICATION_ID = 1002
    }
}

/** OCR 编排：节流抓帧 → ML Kit 中文离线识别 → 复用窗口解析管线入库 */
object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    @Volatile
    var lastOutcome: String = "尚未运行"
        private set

    @Volatile
    private var lastScanAt = 0L

    fun reset() {
        lastOutcome = "尚未运行"
        lastScanAt = 0
    }

    /** 无障碍抓不到文本时的兜底入口；内部节流，重复调用安全 */
    suspend fun maybeScan(context: Context, pkg: String, s: SettingsSnapshot) {
        val service = OcrCaptureService.instance
        if (service == null || !service.isReady) {
            lastOutcome = "屏幕识别未开启"
            return
        }
        if (!s.autoRecordEnabled || pkg !in s.listenScope.packages) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanAt < MIN_INTERVAL_MS) return
        lastScanAt = now

        val bitmap = service.grabFrame()
        if (bitmap == null) {
            lastOutcome = "等待屏幕帧…"
            return
        }
        val visionText = try {
            withContext(Dispatchers.Default) {
                Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            }
        } catch (t: Throwable) {
            lastOutcome = "识别失败：${t.message ?: "未知错误"}"
            return
        } finally {
            bitmap.recycle()
        }
        val lines = visionText.text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        val parsed = WindowCaptureAnalyzer.analyze(lines)
        lastOutcome = if (parsed != null) {
            "${if (parsed.isIncome) "收入" else "支出"} ¥${parsed.amount}（OCR）"
        } else {
            "未发现支付信息（${lines.size} 段文字）"
        }
        if (parsed != null) {
            AutoRecordPipeline.handleWindowTexts(context, pkg, lines, s, origin = "ocr")
        }
    }

    private const val MIN_INTERVAL_MS = 3_000L
}
