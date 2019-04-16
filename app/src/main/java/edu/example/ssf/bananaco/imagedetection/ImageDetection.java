package edu.example.ssf.bananaco.imagedetection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import edu.example.ssf.bananaco.R;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ImageDetection extends Activity {


    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "Placeholder";
    private static final String OUTPUT_NAME = "final_result";
    private static final String MODEL_FILE = "file:///android_asset/bananaco_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/bananaco_labels.txt";
    /*private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/imagenet_comp_graph_label_strings.txt";*/
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "ImageDetection";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Classifier classifier;

    //------------------------------------------------------------
    private TextView textView;
    private ImageView imageViewIsBanana;
    private AtomicLong nextUpdate = new AtomicLong(0);

    private AutoFitTextureView textureView;

    private String mCameraId = "0";

    private CameraDevice cameraDevice;

    private Size previewSize;
    private CaptureRequest.Builder previewRequestBuilder;

    private CaptureRequest previewRequest;

    private CameraCaptureSession captureSession;
    private ImageReader previewReader;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            ImageDetection.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }


        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            ImageDetection.this.cameraDevice = null;
        }


        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            ImageDetection.this.cameraDevice = null;
            ImageDetection.this.finish();
        }
    };
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {

        }
    };

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {

        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth() / 10;
        int h = aspectRatio.getHeight() / 10;
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            System.out.println("Size Error !!!");
            return choices[0];
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imagedetection);

        classifier = TensorFlowImageClassifier.create(
                ImageDetection.this.getAssets(),
                MODEL_FILE,
                LABEL_FILE,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME
        );

        //=================================
        textView = findViewById(R.id.textview);
        textureView = findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(mSurfaceTextureListener);

        imageViewIsBanana = findViewById(R.id.imageViewIsBanana);
        imageViewIsBanana.setImageResource(R.drawable.yes);

    }

    @Override
    protected void onPause() {
        super.onPause();
        System.exit(0);
    }

    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (manager != null) {
                try {

                    setUpCameraOutputs(manager, width, height);
                    manager.openCamera(mCameraId, stateCallback, null);

                } catch (CameraAccessException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, "Failed to get CameraManager", Toast.LENGTH_LONG).show();
                Log.e("camera", "Failed to get CameraManager");
            }
        } else {
            Toast.makeText(this, "Missing permissions to open the camera", Toast.LENGTH_LONG).show();
            Log.e("camera", "Missing permissions to open the camera");
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(previewReader.getSurface());


            cameraDevice.createCaptureSession(
                    Arrays.asList(
                            surface,
                            previewReader.getSurface()
                    ),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {

                            if (null == cameraDevice) {
                                return;
                            }


                            captureSession = cameraCaptureSession;
                            try {

                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                previewRequest = previewRequestBuilder.build();

                                captureSession.setRepeatingRequest(previewRequest, null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(ImageDetection.this, "Config Failed!"
                                    , Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs(@NonNull CameraManager manager, int width, int height) {
        try {

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                    new CompareSizesByArea()
            );

            Log.i("camera", "largest raw: " + largest);

            // TODO otherwise it will not work on newish devices, upper limit not known yet
            largest = new Size(
                    Math.min(largest.getWidth(), INPUT_SIZE * 2),
                    Math.min(largest.getHeight(), INPUT_SIZE * 2)
            );
            Log.i("camera", "largest new: " + largest);

            previewReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.YUV_420_888, 5);
            previewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireNextImage();

                    if (System.currentTimeMillis() > nextUpdate.get()) {
                        nextUpdate.set(Long.MAX_VALUE);
                        new ClassifierTask().execute(image);
                    } else {
                        image.close();
                    }

                }
            }, null);


            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, largest);

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.d(TAG, "NullPointer");
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    class ClassifierTask extends AsyncTask<Image, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Image... images) {
            Image image = images[0];
            final YuvImage yuvImage = new YuvImage(ImageUtil.getDataFromImage(image, ImageUtil.COLOR_FormatNV21), ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream outBitmap = new ByteArrayOutputStream();

            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 95, outBitmap);
            byte[] bytes = outBitmap.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);

            Bitmap croppedBitmap = null;
            try {
                croppedBitmap = ImageUtil.getScaleBitmap(bitmap, INPUT_SIZE);
                croppedBitmap = ImageUtil.rotateBimap(90, croppedBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }

            final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);

            image.close();
            return results.toString();
        }

        @Override
        protected void onPostExecute(String string) {
            super.onPostExecute(string);
            nextUpdate.set(System.currentTimeMillis() + 500);
            textView.setText(String.format("%d -> %d: \n%s", System.currentTimeMillis(), nextUpdate.get(), string));
        }
    }

}