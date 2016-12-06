package com.archronix.borisdetect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.FaceDetector;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.security.InvalidParameterException;

/**
 * Utility class to scan a camera preview stream for faces,
 *  and calculate a displayable Bitmap on demand.
 */
public final class PreviewScannerEx {

    // Implementation note:
    // On some Android BSPs, the Camera preview stream, although reported as NV21,
    // is actually NV12.
    // This class encapsulates these differences.

    private final static String TAG = "PreviewScannerEx";
    private final static int NV12 = 0x103;  // Archronix Android extension

    private final FaceDetector mFaceDetector;
    private final int mWidth, mHeight;
    private final int mExpectedBufferSize;
    private final int mPreviewFormat;

    public PreviewScannerEx(Camera c, int width, int height) {
        mWidth = width;
        mHeight = height;
        String previewFormats = c.getParameters().get("preview-format-values");
        if (previewFormats!=null && previewFormats.contains("fslNV21isNV12"))
            mPreviewFormat = NV12;
        else
            mPreviewFormat = ImageFormat.NV21;
        // both NV12 and NV21 are 12 bits per pixel
        mExpectedBufferSize = getSizeYUV420(mWidth, mHeight);
        mFaceDetector = new FaceDetector(width, height, 1);
    }

    public Frame createFromPreviewData(byte[] data) {
        return new Frame(data);
    }

    public final class Frame {
        private final FaceDetector.Face[] mFaces = new FaceDetector.Face[1];
        private final int mNumFaces;
        private final Bitmap mPreviewBmp;

        Frame(byte[] previewData) {
            if (previewData.length != mExpectedBufferSize)
                throw new InvalidParameterException(
                        "invalid preview frame size");
            // Convert to bitmap the 'native' way, then check for face.
            long startTime = System.nanoTime();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                YuvImage yuvimage = new YuvImage(previewData, mPreviewFormat, mWidth, mHeight, null);
                yuvimage.compressToJpeg(new Rect(0, 0, mWidth, mHeight), 25, baos);
                byte[] bytes = baos.toByteArray();
                mPreviewBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                // WARNING the face detector only works with RGB_565 bitmaps !!
                mNumFaces = mFaceDetector.findFaces(mPreviewBmp.copy(Bitmap.Config.RGB_565, false), mFaces);
            } finally {
                try {
                    baos.close();
                } catch (Exception e) {
                    Log.w(TAG, "Exception while closing stream");
                    e.printStackTrace();
                }
                long stopTime = System.nanoTime();
                Log.d(TAG, "bitmap conversion and face detection took " +
                        (stopTime-startTime)/1000000 + " ms");
            }
        }

        public boolean hasFace() {
            return (mNumFaces>0) && (mFaces[0].confidence() > 0.4f);
        }

        public Bitmap calcDisplayableBitmap() {
            return mPreviewBmp;
        }
    }

    private static int getSizeYUV420(int width, int height) {
        // 12 bits per pixel
        return (width * height * 12 + 7) / 8;
    }
}
