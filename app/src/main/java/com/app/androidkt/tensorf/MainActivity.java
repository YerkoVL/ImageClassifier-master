package com.app.androidkt.tensorf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.app.androidkt.tensorf.tools.Keys;
import com.app.androidkt.tensorf.tools.YourPreference;
import com.app.androidkt.tensorf.util.ImageUtils;
import com.app.androidkt.tensorf.util.Recognition;
import com.kofigyan.stateprogressbar.StateProgressBar;

import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends Activity implements ImageReader.OnImageAvailableListener, CameraFragment.ConnectionCallback {

    public static final String TAG = "MainActivity";

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int INPUT_SIZE = 224;//299
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final String INPUT_NAME = "input";//"Mul:0";
    private static final String OUTPUT_NAME = "final_result";
    private static final String MODEL_FILE = "file:///android_asset/graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/labels.txt";
    private static final boolean MAINTAIN_ASPECT = true;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final float TEXT_SIZE_DIP = 10;
    private Handler handler;
    private HandlerThread handlerThread;
    private Integer sensorOrientation;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap;
    private boolean computing = false;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private TextView resultsView;
    //  private BorderedText borderedText;
    private long lastProcessingTimeMs;
    private TensorFlowImageClassifier classifier;

    private ImageView fingerRight;

    List<Recognition> generalResults;

    YourPreference yourPreference;
    StateProgressBar stateProgressBar;
    Drawable finger;

    int auxFront = 0 , auxBack = 0;

    Context ctx;

    String[] descriptionData = {"30%", "70%", "100%", "Confirm"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        ctx = this;

        stateProgressBar = (StateProgressBar) findViewById(R.id.state_progress_bar);
        stateProgressBar.setStateDescriptionData(descriptionData);

        yourPreference = YourPreference.getInstance(this);
        fingerRight = (ImageView) findViewById(R.id.animRight);

        finger = fingerRight.getDrawable();


        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) || shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(MainActivity.this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    setFragment();
                } else {
                    requestPermission();
                }
            }
        }
    }

    protected void setFragment() {
        final Fragment fragment = new CameraFragment(this);
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    @Override
    protected void onPause() {
        if (!isFinishing()) {
            finish();
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

        Log.d(TAG, "onPreviewSizeChosen");

        classifier = new TensorFlowImageClassifier(getAssets(), MODEL_FILE, LABEL_FILE, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD, INPUT_NAME, OUTPUT_NAME);

        resultsView = findViewById(R.id.results);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();


        sensorOrientation = rotation + screenOrientation;

        rgbBytes = new int[previewWidth * previewHeight];
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        INPUT_SIZE, INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        yuvBytes = new byte[3][];

    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected synchronized void run2InBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;

        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (computing) {
                image.close();
                return;
            }
            computing = true;

            Trace.beginSection("imageAvailable");

            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Trace.endSection();
            return;
        }

        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

        final long startTime = SystemClock.uptimeMillis();
        final List<Recognition> results = classifier.recognizeImage(croppedBitmap);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        runOnUiThread(new Runnable() {
                            @SuppressLint("WrongConstant")
                            @Override
                            public void run() {
                                if (results.size() > 0) {
                                    resultsView.setText(results.toString());
                                    //generalResults = results;
                                    if(auxFront<3) {
                                        scanEnviroment(results);
                                    }else if (auxBack<4){
                                        stateProgressBar.setVisibility(View.VISIBLE);
                                        stateProgressBar.refreshDrawableState();
                                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.ONE);
                                        scanEnviromentBack(results);
                                    }
                                }else {
                                    resultsView.setText("No Identificado");
                                }
                            }
                        });
                    }
                });

        computing = false;
        Trace.endSection();
    }


    private void fillBytes(Image.Plane[] planes, byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private void scanEnviroment(List<Recognition> resultsList){
        float percentant = resultsList.get(0).getConfidence()*100;
        String name = resultsList.get(0).getName().toString();
        if(percentant>=20){
            if(name.equals(Keys.KEY_FRONTAL)){
                //final String partScan = Keys.KEY_FRONTAL;
                auxFront ++;
                switch (auxFront){
                    case 1:
                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.ONE);
                        break;
                    case 2:
                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.TWO);
                        break;
                    case 3:
                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.THREE);
                        break;
                }
                //TOAST FRONTAL
             if(auxFront==3){
                 yourPreference.saveDataFloat(Keys.KEY_FRONTAL,percentant);

                 if(finger instanceof Animatable){
                     fingerRight.setVisibility(View.VISIBLE);
                     new CountDownTimer(4000, 1000) {

                         public void onTick(long millisUntilFinished) {
                             ((Animatable) finger).start();
                             //here you can have your logic to set text to edittext
                         }

                         public void onFinish() {
                             ((Animatable) finger).stop();
                             fingerRight.setVisibility(View.GONE);
                             Toast.makeText(ctx,"Se procederá a escanear la parte trasera",Toast.LENGTH_SHORT).show();
                             stateProgressBar.setVisibility(View.GONE);
                         }

                     }.start();
                 }
             }
            }
        }else{

        }
    }

    private void scanEnviromentBack(List<Recognition> resultsList){
        float percentant = resultsList.get(0).getConfidence()*100;
        String name = resultsList.get(0).getName().toString();
        if(percentant>=20){
            if(name.equals(Keys.KEY_TRASERO)){
                //final String partScan = Keys.KEY_FRONTAL;
                auxBack ++;
                switch (auxBack){
                    case 1:
                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.ONE);
                        break;
                    case 2:
                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.TWO);
                        break;
                    case 3:
                        stateProgressBar.setCurrentStateNumber(StateProgressBar.StateNumber.THREE);
                        break;
                }
                //TOAST FRONTAL
                if(auxBack==3){
                    yourPreference.saveDataFloat(Keys.KEY_TRASERO,percentant);
                    stateProgressBar.setVisibility(View.GONE);
                    finish();
                }
            }
        }else{

        }
    }

}
