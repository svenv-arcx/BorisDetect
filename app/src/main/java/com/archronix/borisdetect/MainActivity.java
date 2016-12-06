package com.archronix.borisdetect;

import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private Camera camera;
    private ImageView faceView;
    private PreviewScannerEx scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceView cameraView = (SurfaceView)this.findViewById(R.id.previewSurface);
        faceView = (ImageView)this.findViewById(R.id.faceView);
        SurfaceHolder holder = cameraView.getHolder();
        holder.addCallback(surfaceCallback);
    }

    private final class MyPreviewCallback implements Camera.PreviewCallback {
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (data == null) {
                return ;
            }
            PreviewScannerEx.Frame frame = scanner.createFromPreviewData(data);
            if (frame.hasFace())
                faceView.setImageBitmap(frame.calcDisplayableBitmap());
            camera.setOneShotPreviewCallback(this);
        }
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = openFrontFacingCamera();
            if (camera == null)
                camera = Camera.open();
            if (camera != null) {
                try {
                    Camera.Parameters p = camera.getParameters();
                    p.setPreviewSize(640, 480);
                    camera.setParameters(p);
                    p = camera.getParameters();
                    int width = p.getPreviewSize().width;
                    int height = p.getPreviewSize().height;
                    scanner = new PreviewScannerEx(camera, width, height);
                    camera.setPreviewDisplay(holder);
                    camera.setOneShotPreviewCallback(new MyPreviewCallback());
                } catch (IOException exception) {
                    camera.release();
                    camera = null;
                }
            }
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            if (camera != null)
                camera.startPreview();
        }
    };

    private static Camera openFrontFacingCamera() {
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int camIndex=0; camIndex<Camera.getNumberOfCameras(); camIndex++) {
                Camera.getCameraInfo(camIndex, info);
                if (Camera.CameraInfo.CAMERA_FACING_FRONT == info.facing)
                    return Camera.open(camIndex);
            }
        } catch (RuntimeException exc) {
            // No camera service
        }
        return null;
    }
}
