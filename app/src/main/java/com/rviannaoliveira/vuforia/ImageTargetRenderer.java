/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.
===============================================================================*/

package com.rviannaoliveira.vuforia;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.rviannaoliveira.vuforia.utils.CubeObject;
import com.rviannaoliveira.vuforia.utils.CubeShaders;
import com.rviannaoliveira.vuforia.utils.LoadingDialogHandler;
import com.rviannaoliveira.vuforia.utils.SampleUtils;
import com.rviannaoliveira.vuforia.utils.Texture;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.Vuforia;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
public class ImageTargetRenderer implements GLSurfaceView.Renderer{
    private static final String LOGTAG = "ImageTargetRenderer";
    private SampleApplicationSession vuforiaAppSession;
    private ImageTargets mActivity;
    private Vector<Texture> mTextures;
    private int shaderProgramID;
    private int vertexHandle;
    private int normalHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;
    private CubeObject mCubeObject;
    private Renderer mRenderer;
    boolean mIsActive = false;
    private static final float OBJECT_SCALE_FLOAT = 45.0f;

    public ImageTargetRenderer(ImageTargets activity,SampleApplicationSession session){
        mActivity = activity;
        vuforiaAppSession = session;
    }
    @Override
    public void onDrawFrame(GL10 gl){
        if (!mIsActive)
            return;
        renderFrame();
    }
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config){
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");
        redenrizaObjetoNaTela();
        vuforiaAppSession.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height){
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");
        vuforiaAppSession.onSurfaceChanged(width, height);
    }

    private void redenrizaObjetoNaTela(){
        mCubeObject = new CubeObject();
        mRenderer = Renderer.getInstance();
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f: 1.0f);

        for (Texture t : mTextures){
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(CubeShaders.CUBE_MESH_VERTEX_SHADER,CubeShaders.CUBE_MESH_FRAGMENT_SHADER);
        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,"vertexPosition");
        normalHandle = GLES20.glGetAttribLocation(shaderProgramID,"vertexNormal");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,"vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,"modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,"texSampler2D");
        mActivity.loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }

    private void renderFrame(){
        State state = pegaEstadoTelaVerificaMatchMapeamento();

        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW); // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++){

            //Pega cada mapeamento
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();
            printUserData(trackable);

            //Ve os assests do mapeamento
            Matrix44F modelViewMatrix_Vuforia = Tool.convertPose2GLMatrix(result.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();
            int textureIndex = trackable.getName().equalsIgnoreCase("stones") ? 0: 1;

            float[] modelViewProjection = new float[16];


            //Dimensiona a escala do objeto
            if (!mActivity.isExtendedTrackingActive()){
                Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,OBJECT_SCALE_FLOAT);
                Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
            }

            Matrix.multiplyMM(modelViewProjection, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrix, 0);
            GLES20.glUseProgram(shaderProgramID);

            seLocalizouMontaObjeto(textureIndex,modelViewProjection);
            SampleUtils.checkGLError("Render Frame");
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        mRenderer.end();
    }

    private State pegaEstadoTelaVerificaMatchMapeamento(){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        State state = mRenderer.begin();
        mRenderer.drawVideoBackground();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        int[] viewport = vuforiaAppSession.getViewport();
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        return state;
    }
    private void seLocalizouMontaObjeto(int textureIndex, float [] modelViewProjection){
        if (!mActivity.isExtendedTrackingActive()){
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,false, 0, mCubeObject.getVertices());
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT,false, 0, mCubeObject.getNormals());
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,GLES20.GL_FLOAT, false, 0, mCubeObject.getTexCoords());
            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glEnableVertexAttribArray(textureCoordHandle);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,mTextures.get(textureIndex).mTextureID[0]);
            GLES20.glUniform1i(texSampler2DHandle, 0);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,modelViewProjection, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mCubeObject.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT, mCubeObject.getIndices());
            GLES20.glDisableVertexAttribArray(vertexHandle);
            GLES20.glDisableVertexAttribArray(normalHandle);
            GLES20.glDisableVertexAttribArray(textureCoordHandle);
        }
    }


    private void printUserData(Trackable trackable){
        String userData = (String) trackable.getUserData();
        Log.d(LOGTAG, "UserData:Retreived User Data	\"" + userData + "\"");
    }

    public void setTextures(Vector<Texture> textures){
        mTextures = textures;
    }

}
