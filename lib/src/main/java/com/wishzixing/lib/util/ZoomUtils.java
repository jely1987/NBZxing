package com.wishzixing.lib.util;

import android.animation.ValueAnimator;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;

import com.wishzixing.lib.manager.CameraManager;
import com.wishzixing.lib.manager.ThreadManager;

/***
 *  Created by SWY
 *  DATE 2019/6/9
 *
 */
public class ZoomUtils {

    static int DURCATION = 300;

    private static ValueAnimator valueAnimator;

    public static void setMaxZoom() {

        Camera camera = CameraManager.get().getCamera();
        if (camera == null)
            return;
        Camera.Parameters p = camera.getParameters();
        if (p == null)
            return;

        if (!p.isZoomSupported())
            return;
        p.setZoom(p.getMaxZoom());
        camera.setParameters(p);
    }

    public static void setMinZoom() {
        Camera camera = CameraManager.get().getCamera();
        if (camera == null)
            return;
        Camera.Parameters p = camera.getParameters();
        if (p == null)
            return;

        if (!p.isZoomSupported())
            return;

        p.setZoom(1);
        camera.setParameters(p);
    }

    public static void zoomToggle() {

        Camera camera = CameraManager.get().getCamera();
        if (camera == null)
            return;
        Camera.Parameters p = camera.getParameters();
        if (p == null)
            return;

        if (!p.isZoomSupported())
            return;

        if (p.getZoom() != p.getMaxZoom())
            p.setZoom(p.getMaxZoom());
        else p.setZoom(1);
        camera.setParameters(p);
    }

    public static void animalZoom(final int target) {
        animalZoom(target, DURCATION);
    }

    public static void animalZoom(final int target, final long milles) {

        final Camera camera = CameraManager.get().getCamera();

        if (camera == null)
            return;

        final Camera.Parameters p = camera.getParameters();
        //防止画面切换闪退
        if (p == null)
            return;

        if (!p.isZoomSupported())
            return;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                if (valueAnimator != null && valueAnimator.isRunning())
                    valueAnimator.cancel();
                valueAnimator = ValueAnimator.ofInt(p.getZoom(), target);
                valueAnimator.setDuration(milles);
                valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {

                        final Camera camera = CameraManager.get().getCamera();
                        if (camera == null)
                            return;
                        final Camera.Parameters p = camera.getParameters();
                        //防止画面切换闪退
                        if (p == null)
                            return;
                        p.setZoom((Integer) animation.getAnimatedValue());
                        camera.setParameters(p);

                    }
                });
                valueAnimator.start();

            }
        });

    }

    public static void setZoom(int zoom) {

        final Camera camera = CameraManager.get().getCamera();
        if (camera == null)
            return;
        final Camera.Parameters p = camera.getParameters();
        //防止画面切换闪退
        if (p == null)
            return;
        if (!p.isZoomSupported())
            return;

        if (zoom > p.getMaxZoom())
            zoom = p.getMaxZoom();

        if (zoom < 1)
            zoom = 1;

        p.setZoom(zoom);
        camera.setParameters(p);

    }

    public static int getZoom() {

        final Camera camera = CameraManager.get().getCamera();

        if (camera == null)
            return 0;

        final Camera.Parameters p = camera.getParameters();
        //防止画面切换闪退
        if (p == null)
            return 0;

        if (!p.isZoomSupported())
            return 0;

        return p.getZoom();
    }

    public static int getMaxZoom() {

        final Camera camera = CameraManager.get().getCamera();

        if (camera == null)
            return 0;

        final Camera.Parameters p = camera.getParameters();
        //防止画面切换闪退
        if (p == null)
            return 0;

        if (!p.isZoomSupported())
            return 0;

        return p.getMaxZoom();
    }

}
