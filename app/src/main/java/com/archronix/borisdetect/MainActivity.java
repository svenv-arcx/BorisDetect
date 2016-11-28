package com.archronix.borisdetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.FaceDetector;
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
    private FaceDetector faceDetector;
    private final FaceDetector.Face[] faces = new FaceDetector.Face[1];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceView cameraView = (SurfaceView)this.findViewById(R.id.previewSurface);
        faceView = (ImageView)this.findViewById(R.id.faceView);
        SurfaceHolder holder = cameraView.getHolder();
        holder.addCallback(surfaceCallback);
    }

    public static Bitmap convertYuvToBitmap(Camera.Size previewSize, byte[] data, int imgf) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            YuvImage yuvimage = new YuvImage(data, imgf, previewSize.width, previewSize.height, null);
            yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 25, baos);
            byte[] bytes = baos.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } finally {
            try {
                baos.close();
            } catch (Exception e) {
            }
        }
    }

    private final class MyPreviewCallback implements Camera.PreviewCallback {
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (data == null) {
                return ;
            }
            Camera.Parameters p = camera.getParameters();
            Bitmap previewBmp = convertYuvToBitmap(p.getPreviewSize(), data, p.getPreviewFormat());
            //Log.e("Sven", "previewBmp width=" + previewBmp.getWidth() + " height=" + previewBmp.getHeight());
            int numOfFaces = faceDetector.findFaces(previewBmp, faces);
            Log.e("Sven", "numOfFaces = " + numOfFaces);
            if (faces[0] != null)
                Log.e("Sven", "confidence = " + faces[0].confidence());
            if ((numOfFaces==1) && (faces[0].confidence() >= 0.4f)) {
                faceView.setImageBitmap(previewBmp);
            }
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
                    faceDetector = new FaceDetector(p.getPreviewSize().width, p.getPreviewSize().height, 1);
                    camera.setPreviewCallback(new MyPreviewCallback());
                    camera.setPreviewDisplay(holder);
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
