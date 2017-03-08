package com.guardanis.imageloader.transitions.drawables;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.bar.test.R;
import com.guardanis.imageloader.stubs.AnimatedStubDrawable;
import com.guardanis.imageloader.stubs.StubDrawable;
import com.guardanis.imageloader.transitions.modules.TransitionModule;

import java.util.HashMap;
import java.util.Map;

import pl.droidsonroids.gif.GifDrawable;

public class TransitionDrawable extends BitmapDrawable {

    protected enum TransitionStage {
        AWAITING_START, TRANSITIONING, FINISHED;
    }

    protected Drawable oldDrawable;
    protected Drawable targetDrawable;

    protected Matrix baseCanvasMatrix = new Matrix();
    protected Matrix canvasMatrixOverride;

    protected Map<Class, TransitionModule> modules = new HashMap<Class, TransitionModule>();

    protected TransitionStage transitionStage = TransitionStage.AWAITING_START;
    protected long animationStart = System.currentTimeMillis();

    protected int overriddenMaxAlpha = 0xFF;

    public TransitionDrawable(Context context, Drawable from, Drawable to, Bitmap canvas) {
        super(context.getResources(), canvas);

        this.oldDrawable = from;
        this.targetDrawable = to;
    }

    public TransitionDrawable registerModule(TransitionModule module){
        if(module != null)
            modules.put(module.getClass(), module);

        return this;
    }

    public void start(Context context){
        this.animationStart = System.currentTimeMillis();
        this.transitionStage = TransitionStage.TRANSITIONING;

        invalidateSelf();

        if(targetDrawable instanceof GifDrawable && context.getResources().getBoolean(R.bool.ail__gif_auto_start_enabled))
            ((GifDrawable) targetDrawable).start();
    }

    public void overrideCanvasMatrix(Matrix canvasMatrixOverride){
        this.canvasMatrixOverride = canvasMatrixOverride;
    }

    @Override
    public void draw(Canvas canvas) {
        if(canvasMatrixOverride != null)
            canvas.setMatrix(canvasMatrixOverride);

        if(transitionStage == TransitionStage.TRANSITIONING){
            boolean unfinishedExists = false;

            for(TransitionModule module : modules.values())
                if(System.currentTimeMillis() < animationStart + module.getDuration())
                    unfinishedExists = true;

            if(unfinishedExists){
                updateModulesAndDraw(canvas, animationStart);

                invalidateSelf();
            }
            else{
                transitionStage = TransitionStage.FINISHED;
                oldDrawable = null;

                handlePostTransitionDrawing(canvas);
            }
        }
        else if(transitionStage == TransitionStage.AWAITING_START)
            updateOldAndDraw(canvas, System.currentTimeMillis());
        else handlePostTransitionDrawing(canvas);

        canvas.getMatrix(baseCanvasMatrix);
    }

    protected void updateModulesAndDraw(Canvas canvas, long startTime){
        updateOldAndDraw(canvas, startTime);

        canvas.save();

        for(TransitionModule module : modules.values())
            module.onPredrawTarget(this, canvas, targetDrawable, startTime);

        drawTarget(canvas);

        canvas.restore();
        safelyRevertTargetDrawables();
    }

    protected void updateOldAndDraw(Canvas canvas, long startTime){
        if (oldDrawable != null){
            canvas.save();

            for(TransitionModule module : modules.values())
                module.onPredrawOld(this, canvas, oldDrawable, startTime);

            drawOldDrawable(canvas);

            canvas.restore();
            safelyRevertOldDrawables();
        }
    }

    protected void drawOldDrawable(Canvas canvas){
        canvas.save();

        int[] translation = calculateStubTranslation(oldDrawable);

        canvas.translate(translation[0], translation[1]);

        oldDrawable.draw(canvas);

        canvas.restore();
    }

    protected void drawTarget(Canvas canvas){
        if(targetDrawable instanceof StubDrawable || targetDrawable instanceof GifDrawable)
            targetDrawable.draw(canvas);
        else super.draw(canvas);
    }

    protected void handlePostTransitionDrawing(Canvas canvas){
        updateModulesAndDraw(canvas, animationStart);

        if(targetDrawable instanceof AnimatedStubDrawable || targetDrawable instanceof GifDrawable)
            invalidateSelf();
    }

    protected int[] calculateStubTranslation(Drawable drawable){
        int halfXDistance = (Math.max(getBounds().right, drawable.getBounds().right) - Math.min(getBounds().right, drawable.getBounds().right)) / 2;
        if(getBounds().right < drawable.getBounds().right)
            halfXDistance *= -1;

        int halfYDistance = (Math.max(getBounds().bottom, drawable.getBounds().bottom) - Math.min(getBounds().bottom, drawable.getBounds().bottom)) / 2;
        if(getBounds().bottom < drawable.getBounds().bottom)
            halfYDistance *= -1;

        return new int[]{ halfXDistance, halfYDistance };
    }

    protected void safelyRevertOldDrawables(){
        for(TransitionModule module : modules.values()){
            try{
                module.revertPostDrawOld(this, oldDrawable);
            }
            catch(NullPointerException e){ } // Likely old drawable is just null
            catch(Throwable e){ e.printStackTrace(); }
        }
    }

    protected void safelyRevertTargetDrawables(){
        for(TransitionModule module : modules.values()){
            try{
                module.revertPostDrawTarget(this, targetDrawable);
            }
            catch(NullPointerException e){ e.printStackTrace(); }
            catch(Throwable e){ e.printStackTrace(); }
        }
    }

    @Override
    public void setAlpha(int alpha){
        int correctedAlpha = Math.min(alpha, overriddenMaxAlpha);

        super.setAlpha(correctedAlpha);
    }

    public void overrideMaxAlphaOut(int overriddenMaxAlpha){
        this.overriddenMaxAlpha = Math.min(overriddenMaxAlpha, this.overriddenMaxAlpha);

        if(oldDrawable != null && oldDrawable instanceof TransitionDrawable)
            ((TransitionDrawable) oldDrawable).overrideMaxAlphaOut(this.overriddenMaxAlpha);
    }

    public int getOverriddenMaxAlpha(){
        return overriddenMaxAlpha;
    }

    public Drawable getTargetDrawable(){
        return targetDrawable;
    }

    public Matrix getBaseCanvasMatrix(){
        return baseCanvasMatrix;
    }

}
