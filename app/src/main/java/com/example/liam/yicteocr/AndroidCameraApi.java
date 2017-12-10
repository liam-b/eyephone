package com.example.liam.yicteocr;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
//import android.widget.Button;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import com.googlecode.tesseract.android.TessBaseAPI;
import android.graphics.Matrix;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class AndroidCameraApi extends AppCompatActivity implements TextToSpeech.OnInitListener, SensorEventListener {
    private static final String TAG = "AndroidCameraApi";
//    private Button takePictureButton;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler handler = new Handler();
    private long delay = 50;

    private SensorManager mSensorManager;
    private Sensor mSensor;

//    private float[] sensorValues = {0.0f, 0.0f, 0.0f};
//    private float[] lastSensorValues = {0.0f, 0.0f, 0.0f};

    private float[] sensorValue = {0.0f, 0.0f, 0.0f};
    private float stillFudge = 17f;

    int numStill = 0;
    int stillAdd = 2;
    int pictureAt = 40;
//    int resetTo = -160;
    int resetTo = -1;

//    UtteranceProgressListener utteranceProgress = new UtteranceProgressListener();

    private List<float[]> sensorValuesList = new ArrayList<float[]>();
    private int keepPastValues = 20;



    public static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/AndroidOCR/";
    public static final String lang = "eng";
    private TessBaseAPI baseApi;

    private TextToSpeech tts;

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {

//            int result = tts.setLanguage(Locale.US);
            int result = tts.setLanguage(new Locale("en","AU"));

//            tts.setOnUtteranceCompletedListener(this);

            // tts.setPitch(5); // set pitch level

            // tts.setSpeechRate(2); // set speech speed rate

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported");
            }

        } else {
            Log.e("TTS", "Initilization Failed");
        }

    }

//    @Override
//    public void onUtteranceCompleted(String utteranceId) {
//        Log.i(TAG, "speaking finished");
//        numStill = 0;
//    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
//        sensorValue = event.values;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            if (event.values != sensorValuesList.get(0)) {
                sensorValuesList.add(0, event.values.clone());

                if (sensorValuesList.size() > keepPastValues) {
                    sensorValuesList.remove(sensorValuesList.size() - 1);
                }
            }
        }

        // Do something with this sensor value.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorValuesList.add(sensorValue);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
//        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
//        assert takePictureButton != null;
//        takePictureButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                takePicture();
//            }
//        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);

        Runnable runner = new Runnable() {
            public void run () {
//                Log.i(TAG, "looping!");
//                Log.i(TAG, String.valueOf(sensorValues[0]));
//                if (sensorValues[0] - lastSensorValues[0] > -0.5 && sensorValues[0] - lastSensorValues[0] < 0.5) {
//                    if (sensorValues[1] - lastSensorValues[1] > -0.5 && sensorValues[1] - lastSensorValues[1] < 0.5) {
//                        if (sensorValues[2] - lastSensorValues[2] > -0.5 && sensorValues[2] - lastSensorValues[2] < 0.5) {
//                            Log.i(TAG, "still!");
//                        }
//                    }
//                }

//                Log.i(TAG, Arrays.toString(sensorValues));
//                Log.i(TAG, Arrays.toString(lastSensorValues));
//                if (sensorValues != lastSensorValues) lastSensorValues = sensorValues;

//                sensorValuesList.add(0, sensorValue);
//
//                if (sensorValuesList.size() > keepPastValues) {
//                    sensorValuesList.remove(sensorValuesList.size() - 1);
//                }

//                Log.i(TAG, Arrays.toString(sensorValue));
//                sensorValuesList.add(sensorValue.clone());
////
                boolean isStill = true;
                float compareMag = magnitude(sensorValuesList.get(0));
                for (float[] values : sensorValuesList) {
//                    Log.i(TAG, Arrays.toString(values));
                    if (magnitude(values) > compareMag + stillFudge || magnitude(values) < compareMag - stillFudge) {
                        isStill = false;
                    }

//                    Log.i(TAG, Float.toString(magnitude(values)) + ", " + Float.toString(compareMag));
//                    Log.i(TAG, Boolean.toString(magnitude(values) > compareMag + stillFudge) + ", " + Boolean.toString(magnitude(values) < compareMag - stillFudge));
                }

//                Log.i(TAG, Boolean.toString(isStill));
//                if (numStill < 0) numStill += 1;

                if (numStill == 0) numStill += 1;
                else if (numStill <= -1) {
                    if (!tts.isSpeaking()) {
                        Log.i(TAG, "stopped speaking");
                        numStill = 0;
                    }
                    numStill += 0;
                }
                else {
                    numStill -= 1;
                    if (isStill) numStill += stillAdd;
                }

                if (numStill > pictureAt) {
                    Log.i(TAG, "pic!");
                    numStill = resetTo;
                    takePicture();
                    numStill = resetTo;
                }

//                  Log.i(TAG, Arrays.toString(sensorValue));

                Log.i(TAG, Integer.toString(numStill));

//                takePicture();
                handler.postDelayed(this, delay);
            }
        };

        handler.postDelayed(runner, delay);

        AssetManager assetManager = getAssets();

        tts = new TextToSpeech(this, this);

        String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.e(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.i(TAG, "Created directory " + path + " on sdcard");
                }
            }
        }

        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                OutputStream out = new FileOutputStream(new File(DATA_PATH + "tessdata/", lang + ".traineddata"));

                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }

        Log.i(TAG, "Before baseApi");

        baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.init(DATA_PATH, lang);
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
//            Toast.makeText(AndroidCameraApi.this, "Saved image:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    float magnitude (float[] vec) {
        return (vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2]);
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    protected void takePicture() {
        Log.d(TAG, "takePicture");
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                        ocr(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }

                private void ocr(byte[] bytes) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    Matrix matrix = new Matrix();
                    matrix.postRotate(270);
                    bitmap =  Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                    baseApi.setImage(bitmap);
                    String recognizedText = baseApi.getUTF8Text();

                    Log.i(TAG, "OCR Result: " + recognizedText);

                    Log.i(TAG, "Speaking the text!");

                    numStill = resetTo;

                    Log.i(TAG, Integer.toString(tts.speak(recognizedText, TextToSpeech.QUEUE_FLUSH, null)));
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
//                    Toast.makeText(AndroidCameraApi.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();

//                    String FILE_PATH = file.getAbsolutePath();

//                    BitmapFactory.Options options = new BitmapFactory.Options();
//                    options.inSampleSize = 2;
//                    Bitmap bitmap = BitmapFactory.decodeFile(FILE_PATH, options);

//                    try {
//                        ExifInterface exif = new ExifInterface(FILE_PATH);
//                        int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
//
//                        Log.v(TAG, "Orient: " + exifOrientation);
//
//                        int rotate = 0;
//                        switch (exifOrientation) {
//                            case ExifInterface.ORIENTATION_ROTATE_90:
//                                rotate = 90;
//                                break;
//                            case ExifInterface.ORIENTATION_ROTATE_180:
//                                rotate = 180;
//                                break;
//                            case ExifInterface.ORIENTATION_ROTATE_270:
//                                rotate = 270;
//                                break;
//                        }
//
//                        Log.v(TAG, "Rotation: " + rotate);
//
//                        if (rotate != 0) {
//
//                            // Getting width & height of the given image.
//                            int w = bitmap.getWidth();
//                            int h = bitmap.getHeight();
//
//                            // Setting pre rotate
//                            Matrix mtx = new Matrix();
//                            mtx.preRotate(rotate);
//
//                            // Rotating Bitmap
//                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
//                            // tesseract req. ARGB_8888
//                            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
//                        }
//
//                    }
//                    catch (IOException e) {
//                        Log.e(TAG, "Rotate or conversion failed: " + e.toString());
//                    }
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(AndroidCameraApi.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(AndroidCameraApi.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(AndroidCameraApi.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        // Don't forget to shutdown!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}