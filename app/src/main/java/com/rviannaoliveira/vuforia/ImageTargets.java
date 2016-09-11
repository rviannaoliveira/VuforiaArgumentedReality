/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.rviannaoliveira.vuforia;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import com.rviannaoliveira.vuforia.utils.LoadingDialogHandler;
import com.rviannaoliveira.vuforia.utils.SampleApplicationGLView;
import com.rviannaoliveira.vuforia.utils.Texture;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import java.util.ArrayList;
import java.util.Vector;


public class ImageTargets extends Activity implements SampleApplicationControl{
    private static final String LOGTAG = "ImageTargets";
    SampleApplicationSession vuforiaAppSession;
    private DataSet mCurrentDataset;
    private ArrayList<String> mDatasetStrings = new ArrayList<String>();
    private SampleApplicationGLView mGlView;
    private ImageTargetRenderer mRenderer;
    private GestureDetector mGestureDetector;
    private Vector<Texture> mTextures;
    private boolean mSwitchDatasetAsap = false;
    private boolean mExtendedTracking = false;
    private RelativeLayout mUILayout;
    LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    private AlertDialog mErrorDialog;
    private final static int STONE_AND_CHIPS =  0;
    private final static int PEDRA_NO_RIM =  1;
    boolean mIsDroidDevice = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState){
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        vuforiaAppSession = new SampleApplicationSession(this);
        startLoadingAnimation();
        mDatasetStrings.add("StonesAndChips.xml");
        mDatasetStrings.add("Image_Targets.xml");
        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mGestureDetector = new GestureDetector(this, new GestureListener());
        mTextures = new Vector<>();
        loadTextures();
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");
    }
    
    private class GestureListener extends GestureDetector.SimpleOnGestureListener{
        private final Handler autofocusHandler = new Handler();

        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e){
            autofocusHandler.postDelayed(new Runnable(){
                public void run(){
                    boolean result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);
            
            return true;
        }
    }

    private void loadTextures(){
        mTextures.add(Texture.loadTextureFromApk("palmeiras.png",getAssets()));
        mTextures.add(Texture.loadTextureFromApk("android.png",getAssets()));
    }
    
    @Override
    protected void onResume(){
        Log.d(LOGTAG, "onResume");
        super.onResume();
        if (mIsDroidDevice){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        try{
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e){
            Log.e(LOGTAG, e.getString());
        }
        
        if (mGlView != null){
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration config){
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        vuforiaAppSession.onConfigurationChanged();
    }
    
    @Override
    protected void onPause(){
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (mGlView != null){
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        try{
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e){
            Log.e(LOGTAG, e.getString());
        }
    }

    @Override
    protected void onDestroy(){
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try{
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)        {
            Log.e(LOGTAG, e.getString());
        }
        mTextures.clear();
        mTextures = null;
        System.gc();
    }
    
    private void initApplicationAR(){
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        mRenderer = new ImageTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
    }

    private void startLoadingAnimation(){
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay,null);
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);
        loadingDialogHandler.mLoadingDialogContainer = mUILayout.findViewById(R.id.loading_indicator);
        loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    }
    
    @Override
    public boolean doLoadTrackersData(){
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;
        
        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();
        
        if (mCurrentDataset == null)
            return false;

        if (!mCurrentDataset.load(mDatasetStrings.get(PEDRA_NO_RIM),STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;

        if (!objectTracker.activateDataSet(mCurrentDataset))
            return false;
        
        int numTrackables = mCurrentDataset.getNumTrackables();
        for (int count = 0; count < numTrackables; count++)
        {
            Trackable trackable = mCurrentDataset.getTrackable(count);
            if(isExtendedTrackingActive())
            {
                trackable.startExtendedTracking();
            }
            
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data "+  trackable.getUserData());
        }
        return true;
    }
    
    
    @Override
    public boolean doUnloadTrackersData(){
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;
        
        if (mCurrentDataset != null && mCurrentDataset.isActive()){
            if (objectTracker.getActiveDataSet().equals(mCurrentDataset)
                && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            } else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }
            mCurrentDataset = null;
        }
        return result;
    }

    @Override
    public void onInitARDone(SampleApplicationException exception){
        if (exception == null){
            initApplicationAR();
            mRenderer.mIsActive = true;
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
            mUILayout.bringToFront();
            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            try{
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e){
                Log.e(LOGTAG, e.getString());
            }
        } else{
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }
    public void showInitializationErrorMessage(String message){
        final String errorMessage = message;
        runOnUiThread(new Runnable(){
            public void run(){
                if (mErrorDialog != null){
                    mErrorDialog.dismiss();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(ImageTargets.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int id){
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }

    @Override
    public void onVuforiaUpdate(State state){
        if (mSwitchDatasetAsap){
            mSwitchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker
                .getClassType());
            if (ot == null || mCurrentDataset == null || ot.getActiveDataSet() == null){
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
            }
            
            doUnloadTrackersData();
            doLoadTrackersData();
        }
    }
    
    @Override
    public boolean doInitTrackers(){
        boolean result = true;
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null){
            Log.e(LOGTAG,"Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else{
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
        return result;
    }

    @Override
    public boolean doStartTrackers(){
        boolean result = true;
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.start();
        
        return result;
    }
    
    @Override
    public boolean doStopTrackers(){
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();
        
        return result;
    }

    @Override
    public boolean doDeinitTrackers(){
        boolean result = true;
        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        return mGestureDetector.onTouchEvent(event);
    }
    boolean isExtendedTrackingActive(){
        return mExtendedTracking;
    }
}
