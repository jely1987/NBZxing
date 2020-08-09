package com.ailiwean.core.able

import android.os.Handler
import android.os.HandlerThread
import com.ailiwean.core.WorkThreadServer
import com.ailiwean.core.helper.ScanHelper
import com.ailiwean.core.zxing.core.PlanarYUVLuminanceSource
import com.ailiwean.module_grayscale.GrayScaleDispatch.dispatch
import java.util.*

/**
 * @Package: com.ailiwean.core.able
 * @ClassName: AbleManager
 * @Description:
 * @Author: SWY
 * @CreateDate: 2020/4/23 2:32 PM
 */
class AbleManager private constructor(handler: Handler) : PixsValuesAble(handler) {

    private val ableList: MutableList<PixsValuesAble> = ArrayList()

    var server: WorkThreadServer
    val grayProcessHandler by lazy {
        Handler(HandlerThread("GrayProcessThread")
                .apply { start() }
                .looper)
    }

    init {
        loadAble()
        server = WorkThreadServer.createInstance()
    }

    fun loadAble() {
        ableList.clear()
        //        ableList.add(new XQRScanAble(handler));
        ableList.add(XQRScanZoomAble(handler))
        ableList.add(XQRScanFastAble(handler))
        ableList.add(XQRScanAbleRotate(handler))
        ableList.add(LighSolveAble(handler))
        ableList.add(RevColorSanAble(handler))
        //        ableList.add(new GrayscaleStrengAble(handler));
    }

    public override fun cusAction(data: ByteArray, dataWidth: Int, dataHeight: Int) {
        executeToParse(data, dataWidth, dataHeight)
        grayscaleProcess(data, dataWidth, dataHeight)
    }

    private fun executeToParse(data: ByteArray, dataWidth: Int, dataHeight: Int) {
        val source = generateGlobeYUVLuminanceSource(data, dataWidth, dataHeight)
        for (able in ableList) {
            server.post {
                able.cusAction(data, dataWidth, dataHeight)
                able.needParseDeploy(source)
            }
        }
    }

    private fun grayscaleProcess(data: ByteArray, dataWidth: Int, dataHeight: Int) {
        grayProcessHandler.post {
            val newByte = dispatch(data, dataWidth, dataHeight)
            executeToParse(newByte, dataWidth, dataHeight)
        }
    }

    private fun generateGlobeYUVLuminanceSource(data: ByteArray?, dataWidth: Int, dataHeight: Int): PlanarYUVLuminanceSource {
        return ScanHelper.buildLuminanceSource(data, dataWidth, dataHeight, ScanHelper.getScanByteRect(dataWidth, dataHeight))
    }

    companion object {
        fun createInstance(handler: Handler): AbleManager {
            return AbleManager(handler)
        }
    }

    fun release() {
        ableList.clear()
        server.quit()
        grayProcessHandler.looper.quit()
    }

}