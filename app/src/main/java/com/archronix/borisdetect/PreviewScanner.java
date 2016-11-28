package com.archronix.borisdetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.FaceDetector;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Utility class to scan a camera preview stream for faces,
 *  and calculate a displayable Bitmap on demand.
 */
public final class PreviewScanner {

    // Implementation note:
    // On some Android BSPs, the Camera preview stream, although reported as NV21,
    // is actually NV12.
    // This class encapsulates these differences.

    private final static String TAG = "PreviewScanner";

    private final FaceDetector mFaceDetector;
    private final int mWidth, mHeight;

    public PreviewScanner(int width, int height) {
        mWidth = width;
        mHeight = height;
        mFaceDetector = new FaceDetector(width, height, 1);
    }

    public Frame createFromPreviewData(byte[] data) {
        return new Frame(data);
    }

    public final class Frame {
        private final FaceDetector.Face[] mFaces = new FaceDetector.Face[1];
        private final int mNumFaces;
        private final byte[] mPreviewData;
        private final Bitmap mPreviewBmp;

        Frame(byte[] previewData) {
            mPreviewData = previewData;
            // Check for a face the 'fast, native' way.
            // It doesn't matter if the preview data is in NV12 or NV21 format -
            // the difference between NV12<->NV21 is a chroma swap, and
            // that shouldn't influence the face detector.
            long startTime = System.nanoTime();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                YuvImage yuvimage = new YuvImage(previewData, ImageFormat.NV21, mWidth, mHeight, null);
                yuvimage.compressToJpeg(new Rect(0, 0, mWidth, mHeight), 25, baos);
                byte[] bytes = baos.toByteArray();
                mPreviewBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                // WARNING the face detector only works with RGB_565 bitmaps !!
                mNumFaces = mFaceDetector.findFaces(mPreviewBmp.copy(Bitmap.Config.RGB_565, false), mFaces);
            } finally {
                try {
                    baos.close();
                } catch (Exception e) {
                }
                long stopTime = System.nanoTime();
                Log.d(TAG, "bitmap conversion + face detection took " +
                        (stopTime-startTime)/1000000 + " ms");
            }
        }

        public boolean hasFace() {
            return (mNumFaces>0) && (mFaces[0].confidence() > 0.4f);
        }

        public Bitmap calcDisplayableBitmap() {
            // If running ICS, the preview stream is NV21, and mPreviewBmp will look ok.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                return mPreviewBmp;
            // On Marshmallow, the preview stream is NV12. Since we haven't got any
            // way to natively convert NV12, we'll do it in Java.
            int[] colours = new int[mWidth*mHeight];
            convertYUV420_NV12toRGB8888(colours, mPreviewData, mWidth, mHeight);
            return Bitmap.createBitmap(colours, mWidth, mHeight, Bitmap.Config.ARGB_8888);
        }
    }

    private static int convertYUVtoARGB(int y, int u, int v) {
        int r,g,b;

        r = y + (int)1.402f*v;
        g = y - (int)(0.344f*u +0.714f*v);
        b = y + (int)1.772f*u;
        r = r>255? 255 : r<0 ? 0 : r;
        g = g>255? 255 : g<0 ? 0 : g;
        b = b>255? 255 : b<0 ? 0 : b;
        return 0xff000000 | (b<<16) | (g<<8) | r;
    }

    /**
     * Converts YUV420 NV12 to RGB565
     *
     * @param result [OUT] result byte array in RGB565 format.
     * @param data [IN]  data byte array in YUV420 NV21 format.
     * @param width [IN]  width pixels width
     * @param height [IN]  height pixels height
     */
    private static void convertYUV420_NV12toRGB8888(int[] result, byte [] data, int width, int height) {
        int size = width*height;
        int u, v, y1, y2, y3, y4;

        for(int i=0, k=0; i < size; i+=2, k+=2) {
            y1 = data[i  ]&0xff;
            y2 = data[i+1]&0xff;
            y3 = data[width+i  ]&0xff;
            y4 = data[width+i+1]&0xff;

            // Sven: swap (u,v) below to decode NV21 preview data coming
            // from the ov5640 camera.
            v = data[size+k  ]&0xff;
            u = data[size+k+1]&0xff;
            u = u-128;
            v = v-128;

            result[i  ] = convertYUVtoARGB(y1, u, v);
            result[i+1] = convertYUVtoARGB(y2, u, v);
            result[width+i  ] = convertYUVtoARGB(y3, u, v);
            result[width+i+1] = convertYUVtoARGB(y4, u, v);

            if (i!=0 && (i+2)%width==0)
                i+=width;
        }
    }
}
