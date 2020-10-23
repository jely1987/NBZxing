package com.google.android.cameraview

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ailiwean.core.Config
import com.ailiwean.core.Utils
import com.ailiwean.core.helper.ZoomHelper
import com.ailiwean.core.view.LifeOwner

/**
 * @Package:        com.google.android.cameraview
 * @ClassName:      BaseCameraView
 * @Description:
 * @Author:         SWY
 * @CreateDate:     2020/4/19 12:02 AM
 */
abstract class BaseCameraView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null, def: Int = 0) :
        CameraView(context, attributeSet, def), LifeOwner {

    //保证避免多次调用start()
    var isShoudCreateOpen = true

    //是否禁止相机
    var isProscribeCamera = false

    init {
        Utils.init(context)
        autoFocus = true
        adjustViewBounds = false
        this.addCallback(object : Callback() {

            var hasFloorView = false

            override fun onCameraOpened(cameraView: CameraView) {
                mainHand.post {
                    onCameraOpenBack(cameraView)
                }
            }

            override fun onCameraClosed(cameraView: CameraView) {
                mainHand.post {
                    onCameraCloseBack(cameraView)
                }
            }

            override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
                mainHand.post {
                    onPictureTakeBack(cameraView, data)
                }
            }

            override fun onPreviewByte(cameraView: CameraView, data: ByteArray?) {
                if (data != null)
                    this@BaseCameraView.onPreviewByteBack(cameraView, data)
            }

        })
    }

    open fun onCameraOpenBack(camera: CameraView) {
        ZoomHelper.toAutoZoom(this)
    }

    open fun onCameraCloseBack(camera: CameraView) {
        ZoomHelper.close(this)
    }

    open fun onPictureTakeBack(camera: CameraView, data: ByteArray) {
    }

    open fun onPreviewByteBack(camera: CameraView, data: ByteArray) {
    }

    /***
     * 绑定AppCompatActivity生命周期并启动相机
     */
    fun synchLifeStart(appCompatActivity: AppCompatActivity) {
        appCompatActivity.lifecycle.addObserver(this)
        appCompatActivity.lifecycle.addObserver(object : LifeOwner {
            //在onCreate()中调用提升相机打开速度
            override fun onCreate() {
                onCameraCreate()
            }

            override fun onResume() {
                onCameraResume()
            }

            override fun onPause() {
                onCameraPause()
            }

            override fun onStop() {

            }

            override fun onDestroy() {

            }
        })
    }

    fun synchLifeStart(fragment: Fragment) {
        fragment.fragmentManager?.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                if (f != fragment) {
                    return
                }
                if (isShoudCreateOpen) {
                    onCreate()
                    onResume()
                    onCameraCreate()
                } else {
                    onResume()
                    onCameraResume()
                }
            }

            override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
                if (f != fragment) {
                    return
                }
                onPause()
                onCameraPause()
            }


            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                if (f != fragment) {
                    return
                }
                onStop()
            }

            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                if (f != fragment) {
                    return
                }
                onDestroy()
            }
        }, false)
    }

    private fun onCameraCreate() {
        if (!isShoudCreateOpen)
            return
        if (Utils.checkPermissionCamera(context)) {
            openCameraBefore()
            openCamera()
        } else {
            Utils.requstPermission(context)
        }
    }

    protected fun onCameraResume() {
        if (isShoudCreateOpen) {
            return
        }
        if (Utils.checkPermissionCamera(context) && !isCameraOpened) {
            openCameraBefore()
            openCamera()
        }
    }

    protected fun onCameraPause() {
        closeCameraBefore()
        closeCamera()
        isShoudCreateOpen = false
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        super.setAspectRatio(ratio)
        //相机运行过程中切换比例
        if (isCameraOpened) {
            closeCameraBefore()
            openCameraBefore()
            cameraHandler.removeCallbacksAndMessages(null)
            cameraHandler.post {
                stop()
                start()
            }
        }
    }

    private val cameraHandler by lazy {
        val handlerThread = HandlerThread(System.currentTimeMillis().toString())
        handlerThread.start()
        Handler(handlerThread.looper)
                .apply {
                    provideCameraHandler(this)
                }
    }

    private fun openCamera() {
        cameraHandler.removeCallbacksAndMessages(null)
        cameraHandler.post {
            if (!isProscribeCamera)
                start()
        }
    }

    private fun closeCamera() {
        cameraHandler.removeCallbacksAndMessages(null)
        cameraHandler.post {
            stop()
        }
    }

    /***
     * 数字变焦
     */
    fun setZoom(percent: Float) {
        when {
            percent >= 1f -> mImpl.toZoomMax()
            percent <= 0f -> mImpl.toZoomMin()
            else -> mImpl.setZoom(percent)
        }
        //捕获当前倍率
        Config.currentZoom = percent.let {
            when {
                it <= 0 -> 0f
                it >= 1 -> 1f
                else -> it
            }
        }
    }

    /***
     * 打开/关闭 闪光灯
     */
    fun lightOperator(isOpen: Boolean) {
        mImpl.lightOperator(isOpen)
    }

    override fun onCreate() {
    }

    override fun onResume() {
    }

    override fun onPause() {
    }

    override fun onStop() {

    }

    override fun onDestroy() {
        cameraHandler.looper.quit()
    }

    /***
     * 禁止相机启用
     */
    fun proscribeCamera() {
        isProscribeCamera = true
        onCameraPause()
    }

    /***
     * 允许并启用
     */
    fun unProscibeCamera() {
        isProscribeCamera = false
        onCameraResume()
    }
}