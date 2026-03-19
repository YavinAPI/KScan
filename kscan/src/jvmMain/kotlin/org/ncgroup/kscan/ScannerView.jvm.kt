package org.ncgroup.kscan

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.CanvasFrame
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber
import java.awt.image.BufferedImage
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

@Composable
actual fun ScannerView(
    modifier: Modifier,
    codeTypes: List<BarcodeFormat>,
    colors: ScannerColors,
    scannerUiOptions: ScannerUiOptions?,
    scannerController: ScannerController?,
    filter: (Barcode) -> Boolean,
    result: (BarcodeResult) -> Unit
) {
    val updatedResult by rememberUpdatedState(result)
    val coroutineScope = rememberCoroutineScope()
    var grabber by remember { mutableStateOf<OpenCVFrameGrabber?>(null) }
    val canvasFrame = remember { 
        CanvasFrame("Scanner").apply {
            isVisible = false
        }
    }
    var isScanning by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val defaultGrabber = OpenCVFrameGrabber(0)

                defaultGrabber.imageWidth = 1280
                defaultGrabber.imageHeight = 720
                defaultGrabber.start()

                grabber = defaultGrabber
            } catch (e: Exception) {
                updatedResult(BarcodeResult.OnFailed(e))
                return@launch
            }
        }

        onDispose {
            grabber?.stop()
            canvasFrame.dispose()
        }
    }

    if (grabber != null) {
        val converter = remember { Java2DFrameConverter() }

        DisposableEffect(Unit) {
            val frameChannel = Channel<BufferedImage>(Channel.CONFLATED)

            val cameraJob = coroutineScope.launch(Dispatchers.IO) {
                while (isActive) {
                    val frame = grabber?.grab() ?: continue
                    canvasFrame.showImage(frame)

                    val image = converter.convert(frame)

                    frameChannel.trySend(image)

                    delay(10.milliseconds)
                }
            }

            val scannerJob = coroutineScope.launch(Dispatchers.Default) {
                val reader = MultiFormatReader()

                val hints: MutableMap<DecodeHintType, Any> = EnumMap(DecodeHintType::class.java)
                val formats = codeTypes.mapNotNull { it.toZxingFormat() }.ifEmpty {
                    listOf(com.google.zxing.BarcodeFormat.QR_CODE)
                }
                hints[DecodeHintType.POSSIBLE_FORMATS] = formats
                reader.setHints(hints)

                for (image in frameChannel) {
                    if (!isActive || !isScanning) break

                    try {
                        val source = BufferedImageLuminanceSource(image)
                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                        val result = reader.decode(binaryBitmap)

                        withContext(Dispatchers.Main) {
                            val barcode = Barcode(
                                data = result.text,
                                format = result.barcodeFormat.toKScanFormat().toString(),
                                rawBytes = result.rawBytes
                            )

                            if (filter(barcode)) {
                                withContext(Dispatchers.Main) {
                                    isScanning = false
                                    updatedResult(BarcodeResult.OnSuccess(barcode))
                                }
                            }
                        }
                    } catch (_: NotFoundException) {
                        // no barcode found -> next image
                    } catch (e: Exception) {
                        updatedResult(BarcodeResult.OnFailed(e))
                    }
                }
            }

            onDispose {
                cameraJob.cancel()
                scannerJob.cancel()
                frameChannel.close()
            }
        }
    }

    ScannerViewContent(
        modifier = modifier,
        colors = colors,
        scannerUiOptions = scannerUiOptions,
        torchEnabled = false,
        onTorchEnabled = {},
        zoomRatio = 1f,
        onZoomChange = {},
        maxZoomRatio = 1f,
        onCancel = {
            isScanning = false
            updatedResult(BarcodeResult.OnCanceled)
        }
    ) {
        if (grabber != null) {
            SwingPanel(
                factory = {
                    canvasFrame.canvas
                },
                modifier = Modifier.fillMaxSize(),
                update = { }
            )
        }
    }
}
