package org.renpy.android;

import org.libsdl.app.SDLActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class PythonSDLActivity extends SDLActivity {

    /**
     * This exists so python code can access this activity.
     */
    public static PythonSDLActivity mActivity = null;

    /**
     * The layout that contains the SDL view. VideoPlayer uses this to add
     * its own view on on top of the SDL view.
     */
    public FrameLayout mFrameLayout;

    /**
     * A layout that contains mLayout. This is a 3x3 grid, with the layout
     * in the center. The idea is that if someone wants to show an ad, they
     * can stick it in one of the other cells..
     */
    public LinearLayout mVbox;

    /**
     * This is set by the renpy.iap.Store when it's loaded. If it's not loadable, this
     * remains null;
     */
    public StoreInterface mStore = null;

    public String mGamePath = "";
    public String mGameName = "";
    public String mEnginePath = "";
    public String mEngineVersion = "";
    public String mEngineZip = "";
    public View mLoadingContainer = null;
    public long sessionStartTime = 0;

    ResourceManager resourceManager;

    protected String[] getLibraries() {
        return new String[] {
            "renpython",
        };
    }

    @Override
    protected String[] getArguments() {
        if (mGamePath != null && !mGamePath.isEmpty()) {
            return new String[] { mGamePath };
        }
        return new String[0];
    }

    // Creates the IAP store, when needed. /////////////////////////////////////////

    public void createStore() {
        if (Constants.store.equals("none")) {
            return;
        }

        try {
            Class<?> cls = Class.forName("org.renpy.iap.Store");
            cls.getMethod("create", PythonSDLActivity.class).invoke(null, this);
        } catch (Exception e) {
            Log.e("PythonSDLActivity", "Failed to create store: " + e.toString());
        }
    }

    // GUI code. /////////////////////////////////////////////////////////////

    public void addView(View view, int index) {
        mVbox.addView(view, index, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, (float) 0.0));
    }

    public void removeView(View view) {
        mVbox.removeView(view);
    }

    @Override
    public void setContentView(View view) {
        mFrameLayout = new FrameLayout(this);
        mFrameLayout.addView(view);

        mVbox = new LinearLayout(this);
        mVbox.setOrientation(LinearLayout.VERTICAL);
        mVbox.addView(mFrameLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (float) 1.0));

        super.setContentView(mVbox);
    }


    // Overriding this makes SDL respect the orientation given in the Android
    // manifest.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        return;
    }

    // Code to unpack python and get things running ///////////////////////////

    public void recursiveDelete(File f) {
        if (f.isDirectory()) {
            for (File r : f.listFiles()) {
                recursiveDelete(r);
            }
        }
        f.delete();
    }

    /**
     * This determines if unpacking one the zip files included in
     * the .apk is necessary. If it is, the zip file is unpacked.
     */
    public void unpackData(final String archivePath, File target) {

        new File(target, "main.pyo").delete();

        boolean shouldUnpack = false;
        String data_version = "extracted_" + archivePath.hashCode();
        String disk_version = null;
        String disk_version_fn = target.getAbsolutePath() + "/.version";

        try {
            byte buf[] = new byte[64];
            InputStream is = new FileInputStream(disk_version_fn);
            int len = is.read(buf);
            disk_version = new String(buf, 0, len);
            is.close();
        } catch (Exception e) {
            disk_version = "";
        }

        if (!data_version.equals(disk_version)) {
            shouldUnpack = true;
        }

        if (shouldUnpack) {
            Log.v("python", "Extracting " + archivePath + " assets.");

            recursiveDelete(new File(target, "lib"));
            recursiveDelete(new File(target, "renpy"));

            target.mkdirs();

            AssetExtract ae = new AssetExtract(this);
            if (!ae.extractTar(archivePath, target.getAbsolutePath())) {
                toastError("Could not extract " + archivePath + " data.");
            }

            try {
                new File(target, ".nomedia").createNewFile();
                FileOutputStream os = new FileOutputStream(disk_version_fn);
                os.write(data_version.getBytes());
                os.close();
            } catch (Exception e) {
                Log.w("python", e);
            }
        }

    }

    /**
     * Show an error using a toast. (Only makes sense from non-UI
     * threads.)
     */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(new Runnable () {
            public void run() {
                Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    public native void nativeSetEnv(String variable, String value);

    public void preparePython() {
        Log.v("python", "Starting preparePython.");

        mActivity = this;

        android.content.SharedPreferences prefs = getSharedPreferences("RenPlayPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("hw_video", true)) {
            nativeSetEnv("RENPY_HW_VIDEO", "1");
        } else {
            nativeSetEnv("RENPY_HW_VIDEO", "0");
        }

        if (prefs.getBoolean("phone_variant", false)) {
            nativeSetEnv("RENPY_VARIANT", "mobile touch phone small");
        } else {
            nativeSetEnv("RENPY_VARIANT", "pc large");
        }

        if (prefs.getBoolean("model_rendering", true)) {
            nativeSetEnv("RENPY_GL_MODEL", "1");
        } else {
            nativeSetEnv("RENPY_GL_MODEL", "0");
        }

        if (prefs.getBoolean("force_recompile", false)) {
            nativeSetEnv("RENPY_RECOMPILE", "1");
        }

        resourceManager = new ResourceManager(this);

        File oldExternalStorage = new File(Environment.getExternalStorageDirectory(), getPackageName());
        File externalStorage = getExternalFilesDir(null);
        File path;

        if (externalStorage == null) {
            externalStorage = oldExternalStorage;
        }

        File privateDir = new File(mEnginePath, "private");
        unpackData(mEnginePath + "/private.mp3", privateDir);

        nativeSetEnv("ANDROID_PRIVATE", privateDir.getAbsolutePath());

        if (mGamePath != null && !mGamePath.isEmpty()) {
            nativeSetEnv("ANDROID_PUBLIC", mGamePath);
            nativeSetEnv("RENPY_SAVE_PATH", getFilesDir().getAbsolutePath() + "/saves/" + mGamePath.hashCode());
        } else {
            nativeSetEnv("ANDROID_PUBLIC", externalStorage.getAbsolutePath());
        }
        nativeSetEnv("ANDROID_OLD_PUBLIC", oldExternalStorage.getAbsolutePath());

        nativeSetEnv("ANDROID_APK", mEngineZip);

        if (!mAllPacksReady) {
            Log.i("python", "Waiting for all packs to become ready.");
        }

        synchronized (this) {
            while (!mAllPacksReady) {
                try {
                    this.wait();
                } catch (InterruptedException e) { /* pass */ }
            }
        }

        Log.v("python", "Finished preparePython.");

    };

    // App lifecycle.
    public ImageView mPresplash = null;

    Bitmap getBitmap(String assetName) {
        try {
            InputStream is = getAssets().open(assetName);
            Bitmap rv = BitmapFactory.decodeStream(is);
            is.close();

            return rv;
        } catch (IOException e) {
            return null;
        }
    }

    boolean mAllPacksReady = true;

    // The pack download progress bar.
    ProgressBar mProgressBar = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v("python", "onCreate()");
        if (getIntent() != null) {
            if (getIntent().hasExtra("GAME_PATH")) mGamePath = getIntent().getStringExtra("GAME_PATH");
            if (getIntent().hasExtra("GAME_NAME")) mGameName = getIntent().getStringExtra("GAME_NAME");
            if (getIntent().hasExtra("ENGINE_PATH")) mEnginePath = getIntent().getStringExtra("ENGINE_PATH");
            if (getIntent().hasExtra("ENGINE_VERSION")) mEngineVersion = getIntent().getStringExtra("ENGINE_VERSION");
            if (getIntent().hasExtra("ENGINE_ZIP")) mEngineZip = getIntent().getStringExtra("ENGINE_ZIP");
            if (getIntent().hasExtra("ENGINE_LIB")) SDLActivity.mEngineLibPath = getIntent().getStringExtra("ENGINE_LIB");
        }
        super.onCreate(savedInstanceState);

        if (mLayout == null) {
            return;
        }

        // Initalize the store support.
        createStore();

        mAllPacksReady = true;

        String bitmapFilename;

        if (mAllPacksReady) {
            bitmapFilename = "android-presplash";
        } else {
            bitmapFilename = "android-downloading";
        }

        // Show the presplash.
        Bitmap presplashBitmap = getBitmap(bitmapFilename + ".png");

        if (presplashBitmap == null) {
            presplashBitmap = getBitmap(bitmapFilename + ".jpg");
        }

        if (presplashBitmap != null) {

            mPresplash = new ImageView(this);
            mPresplash.setBackgroundColor(presplashBitmap.getPixel(0, 0));
            mPresplash.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mPresplash.setImageBitmap(presplashBitmap);

            mLayout.addView(mPresplash, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        }

        if (!mAllPacksReady) {
            RelativeLayout.LayoutParams prlp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, 20);
            prlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            prlp.leftMargin = 20;
            prlp.rightMargin = 20;
            prlp.bottomMargin = 20;

            mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            mLayout.addView(mProgressBar, prlp);
        }

        createLoadingCard();
    }

    private void createLoadingCard() {
        mLoadingContainer = ru.reset.renplay.utils.LoadingOverlayHelper.createLoadingOverlay(this, mGameName, mEngineVersion, mGamePath);
        mLayout.addView(mLoadingContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Called by Ren'Py to hide the presplash after start.
     */
    public void hidePresplash() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mActivity.mLoadingContainer != null) {
                    mActivity.mLayout.removeView(mActivity.mLoadingContainer);
                    ru.reset.renplay.utils.LoadingOverlayHelper.destroyLoadingOverlay(mActivity.mLoadingContainer);
                    mActivity.mLoadingContainer = null;
                }

                if (mActivity.mPresplash != null) {
                    mActivity.mPresplash.animate().alpha(0f).setDuration(350).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (mActivity.mPresplash != null) {
                                mActivity.mLayout.removeView(mActivity.mPresplash);
                                mActivity.mPresplash = null;
                            }
                        }
                    }).start();
                }

                if (mActivity.mProgressBar != null) {
                    mActivity.mLayout.removeView(mActivity.mProgressBar);
                    mActivity.mProgressBar = null;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.v("python", "onDestroy()");

        super.onDestroy();

        if (mStore != null) {
            mStore.destroy();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.v("python", "onNewIntent()");
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGamePath != null && !mGamePath.isEmpty()) {
            sessionStartTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGamePath != null && !mGamePath.isEmpty() && sessionStartTime > 0) {
            long sessionDuration = System.currentTimeMillis() - sessionStartTime;
            sessionStartTime = 0;
            try {
                File statFile = new File(getFilesDir(), "playtime_" + mGamePath.hashCode() + ".stat");
                long total = 0;
                long today = 0;
                String lastDate = "";
                String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());

                if (statFile.exists()) {
                    BufferedReader br = new BufferedReader(new FileReader(statFile));
                    String line = br.readLine();
                    br.close();
                    if (line != null) {
                        String[] parts = line.split(":");
                        if (parts.length >= 3) {
                            total = Long.parseLong(parts[0]);
                            today = Long.parseLong(parts[1]);
                            lastDate = parts[2];
                        }
                    }
                }

                if (!currentDate.equals(lastDate)) {
                    today = 0;
                }

                total += sessionDuration;
                today += sessionDuration;

                FileWriter fw = new FileWriter(statFile);
                fw.write(total + ":" + today + ":" + currentDate);
                fw.close();
            } catch (Exception e) {
                Log.e("PythonSDLActivity", "Failed to save playtime", e);
            }
        }
    }

    public boolean mStopDone = true;

    @Override
    public void onStop() {
        Log.v("python", "onStop() start.");

        super.onStop();
        long startTime = System.currentTimeMillis();

        synchronized (this) {
            while (true) {
                if (mStopDone) {
                    break;
                }

                // Backstop.
                if (startTime + 8000 < System.currentTimeMillis()) {
                    break;
                }

                try {
                    this.wait(100);
                } catch (InterruptedException e) { /* pass */ }

            }
        }

        Log.v("python", "onStop() done.");
    }

    public void armOnStop () {
        Log.v("python", "armOnStop()");
        mStopDone = false;
    }

    public void finishOnStop() {
        Log.v("python", "finishOnStop()");

        synchronized (this) {
            mStopDone = true;
            this.notifyAll();
        }
    }

    // Support public APIs. ////////////////////////////////////////////////////

    public void openUrl(String url) {
        openURL(url);
    }

    public void openEditor(String file) {
		File f = new File(file);

		Uri uri = null;
		if (Build.VERSION.SDK_INT >= 24) {
			uri = RenPyFileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
		} else {
			uri = Uri.fromFile(f);
		}

		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(uri, "text/plain");
		i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    public void vibrate(double s) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
			if (Build.VERSION.SDK_INT >= 26) {
				v.vibrate(VibrationEffect.createOneShot((int) (1000 * s), VibrationEffect.DEFAULT_AMPLITUDE));
			} else {
				v.vibrate((int) (1000 * s));
			}
		}
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    public PowerManager.WakeLock wakeLock = null;

    public void setWakeLock(boolean active) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
            wakeLock.setReferenceCounted(false);
        }

        if (active) {
            wakeLock.acquire();
        } else {
            wakeLock.release();
        }
    }

    // Activity Requests ///////////////////////////////////////////////////////

    // The thought behind this is that this will make it possible to call
    // mActivity.startActivity(Intent, requestCode), then poll the fields on
    // this object until the response comes back.

    public int mActivityResultRequestCode = -1;
    public int mActivityResultResultCode = -1;
    public Intent mActivityResultResultData = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (mStore != null && mStore.onActivityResult(requestCode, resultCode, resultData)) {
            return;
        }

        Log.v("python", "onActivityResult(" + requestCode + ", " + resultCode + ", " + resultData.toString() + ")");

        mActivityResultRequestCode = requestCode;
        mActivityResultResultCode = resultCode;
        mActivityResultResultData = resultData;

        super.onActivityResult(requestCode, resultCode, resultData);
    }
}
