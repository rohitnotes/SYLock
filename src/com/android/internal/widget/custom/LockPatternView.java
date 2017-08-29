/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.widget.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.IntArray;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.example.locknumber.R;

/**
 * Displays and detects the user's unlock attempt, which is a drag of a finger
 * across 9 regions of the screen.
 *
 * Is also capable of displaying a static pattern in "in progress", "wrong" or
 * "correct" states.
 */

// cp from com.android.internal.widget.LockPatternView (Android M, MTK)
public class LockPatternView extends View {
    // Aspect to use when rendering this view
    private static final int ASPECT_SQUARE = 0; // View will be the minimum of width/height
    private static final int ASPECT_LOCK_WIDTH = 1; // Fixed width; height will be minimum of (w,h)
    private static final int ASPECT_LOCK_HEIGHT = 2; // Fixed height; width will be minimum of (w,h)

    private static final boolean PROFILE_DRAWING = false;
    private final CellState[][] mCellStates;

    private final int mDotSize;
    private final int mDotSizeActivated;
    private final int mPathWidth;

    private boolean mDrawingProfilingStarted = false;

    private final Paint mPaint = new Paint();
    private final Paint mPathPaint = new Paint();

    /**
     * How many milliseconds we spend animating each circle of a lock pattern
     * if the animating mode is set.  The entire animation should take this
     * constant * the length of the pattern to complete.
     */
    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;

    /**
     * This can be used to avoid updating the display for very small motions or noisy panels.
     * It didn't seem to have much impact on the devices tested, so currently set to 0.
     */
    private static final float DRAG_THRESHHOLD = 0.0f;
    public static final int VIRTUAL_BASE_VIEW_ID = 1;
    public static final boolean DEBUG_A11Y = false;
    private static final String TAG = "LockPatternView";

    private OnPatternListener mOnPatternListener;
    private final ArrayList<Cell> mPattern = new ArrayList<Cell>(9);

    /**
     * Lookup table for the circles of the pattern we are currently drawing.
     * This will be the cells of the complete pattern unless we are animating,
     * in which case we use this to hold the cells we are drawing for the in
     * progress animation.
     */
    private final boolean[][] mPatternDrawLookup = new boolean[3][3];

    /**
     * the in progress point:
     * - during interaction: where the user's finger is
     * - during animation: the current tip of the animating line
     */
    private float mInProgressX = -1;
    private float mInProgressY = -1;

    private long mAnimatingPeriodStart;

    private DisplayMode mPatternDisplayMode = DisplayMode.Correct;
    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private boolean mEnableHapticFeedback = true;
    private boolean mPatternInProgress = false;

    private float mHitFactor = 0.6f;

    private float mSquareWidth;
    private float mSquareHeight;

    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();
    private final Rect mTmpInvalidateRect = new Rect();

    private int mAspect;
    private int mRegularColor;
    private int mErrorColor;
    private int mSuccessColor;

    private final Interpolator mFastOutSlowInInterpolator;
    private final Interpolator mLinearOutSlowInInterpolator;
    private PatternExploreByTouchHelper mExploreByTouchHelper;
    private Context mContext;
    private int mPaddingLeft;
    private int mPaddingRight;
    private int mPaddingTop;
    private int mPaddingBottom;
    private AccessibilityManager mAccessibilityManager;

    /**
     * Represents a cell in the 3 X 3 matrix of the unlock pattern view.
     */
    public static final class Cell {
        final int row;
        final int column;

        // keep # objects limited to 9
        private static final Cell[][] sCells = createCells();

        private static Cell[][] createCells() {
            Cell[][] res = new Cell[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    res[i][j] = new Cell(i, j);
                }
            }
            return res;
        }

        /**
         * @param row The row of the cell.
         * @param column The column of the cell.
         */
        private Cell(int row, int column) {
            checkRange(row, column);
            this.row = row;
            this.column = column;
        }

        public int getRow() {
            return row;
        }

        public int getColumn() {
            return column;
        }

        public int getValue() {
            return row*3+column;
        }

        public static Cell of(int row, int column) {
            checkRange(row, column);
            return sCells[row][column];
        }

        private static void checkRange(int row, int column) {
            if (row < 0 || row > 2) {
                throw new IllegalArgumentException("row must be in range 0-2");
            }
            if (column < 0 || column > 2) {
                throw new IllegalArgumentException("column must be in range 0-2");
            }
        }

        float scale = 1.0f;
        public float getScale() {
            return scale;
        }

        public void setScale(float scale) {
            this.scale = scale;
        }

        @Override
        public String toString() {
            return "(row=" + row + ",clmn=" + column + ")";
        }
    }

    public static class CellState {
        int row;
        int col;
        boolean hwAnimating;
        float radius;
        float translationY;
        float alpha = 1f;
        public ValueAnimator lineAnimator;
     }

    /**
     * How to display the current pattern.
     */
    public enum DisplayMode {

        /**
         * The pattern drawn is correct (i.e draw it in a friendly color)
         */
        Correct,

        /**
         * Animate the pattern (for demo, and help).
         */
        Animate,

        /**
         * The pattern is wrong (i.e draw a foreboding color)
         */
        Wrong
    }

    /**
     * The call back interface for detecting patterns entered by the user.
     */
    public static interface OnPatternListener {

        /**
         * A new pattern has begun.
         */
        void onPatternStart();

        /**
         * The pattern was cleared.
         */
        void onPatternCleared();

        /**
         * The user extended the pattern currently being drawn by one cell.
         * @param pattern The pattern with newly added cell.
         */
        void onPatternCellAdded(List<Cell> pattern);

        /**
         * A pattern was detected from the user.
         * @param pattern The pattern.
         */
        void onPatternDetected(List<Cell> pattern);
    }

    public LockPatternView(Context context) {
        this(context, null);
    }

    public LockPatternView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        setClickable(true);

        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);

        mRegularColor = getResources().getColor(R.color.lock_pattern_view_regular_color);
        mErrorColor = getResources().getColor(R.color.lock_pattern_view_error_color);
        mSuccessColor = getResources().getColor(R.color.lock_pattern_view_success_color);

        mPathWidth = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_line_width);
        mDotSize = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_size);
        mDotSizeActivated = getResources().getDimensionPixelSize(
                R.dimen.lock_pattern_dot_size_activated);

        mPathPaint.setColor(mRegularColor);

        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);

        mPathPaint.setStrokeWidth(2);
        mPaint.setStrokeWidth(2);

        mPaint.setAntiAlias(true);
        mPaint.setDither(true);

        mCellStates = new CellState[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mCellStates[i][j] = new CellState();
                mCellStates[i][j].radius = mDotSize/2;
                mCellStates[i][j].row = i;
                mCellStates[i][j].col = j;
            }
        }

        mFastOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        mLinearOutSlowInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in);

        mExploreByTouchHelper = new PatternExploreByTouchHelper(this);
        setAccessibilityDelegate(mExploreByTouchHelper);

        mAccessibilityManager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    }

    public CellState[][] getCellStates() {
        return mCellStates;
    }

    /**
     * @return Whether the view is in stealth mode.
     */
    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    /**
     * @return Whether the view has tactile feedback enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return mEnableHapticFeedback;
    }

    /**
     * Set whether the view is in stealth mode.  If true, there will be no
     * visible feedback as the user enters the pattern.
     *
     * @param inStealthMode Whether in stealth mode.
     */
    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    /**
     * Set whether the view will use tactile feedback.  If true, there will be
     * tactile feedback as the user enters the pattern.
     *
     * @param tactileFeedbackEnabled Whether tactile feedback is enabled
     */
    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    /**
     * Set the call back for pattern detection.
     * @param onPatternListener The call back.
     */
    public void setOnPatternListener(
            OnPatternListener onPatternListener) {
        mOnPatternListener = onPatternListener;
    }

    /**
     * Set the pattern explicitely (rather than waiting for the user to input
     * a pattern).
     * @param displayMode How to display the pattern.
     * @param pattern The pattern.
     */
    public void setPattern(DisplayMode displayMode, List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Cell cell : pattern) {
            mPatternDrawLookup[cell.getRow()][cell.getColumn()] = true;
        }

        setDisplayMode(displayMode);
    }

    /**
     * Set the display mode of the current pattern.  This can be useful, for
     * instance, after detecting a pattern to tell this view whether change the
     * in progress result to correct or wrong.
     * @param displayMode The display mode.
     */
    public void setDisplayMode(DisplayMode displayMode) {
        mPatternDisplayMode = displayMode;
        if (displayMode == DisplayMode.Animate) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException("you must have a pattern to "
                        + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Cell first = mPattern.get(0);
            mInProgressX = getCenterXForColumn(first.getColumn());
            mInProgressY = getCenterYForRow(first.getRow());
            clearPatternDrawLookup();
        }
        invalidate();
    }

    public void startCellStateAnimation(CellState cellState, float startAlpha, float endAlpha,
            float startTranslationY, float endTranslationY, float startScale, float endScale,
            long delay, long duration,
            Interpolator interpolator, Runnable finishRunnable) {
        startCellStateAnimationSw(cellState, startAlpha, endAlpha, startTranslationY,
                endTranslationY, startScale, endScale, delay, duration, interpolator,
                finishRunnable);
    }

    private void startCellStateAnimationSw(final CellState cellState,
            final float startAlpha, final float endAlpha,
            final float startTranslationY, final float endTranslationY,
            final float startScale, final float endScale,
            long delay, long duration, Interpolator interpolator, final Runnable finishRunnable) {
        cellState.alpha = startAlpha;
        cellState.translationY = startTranslationY;
        cellState.radius = mDotSize/2 * startScale;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setStartDelay(delay);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (float) animation.getAnimatedValue();
                cellState.alpha = (1 - t) * startAlpha + t * endAlpha;
                cellState.translationY = (1 - t) * startTranslationY + t * endTranslationY;
                cellState.radius = mDotSize/2 * ((1 - t) * startScale + t * endScale);
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (finishRunnable != null) {
                    finishRunnable.run();
                }
            }
        });
        animator.start();
    }

    private void notifyCellAdded() {
        // sendAccessEvent(R.string.lockscreen_access_pattern_cell_added);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCellAdded(mPattern);
        }
        // Disable used cells for accessibility as they get added
        if (DEBUG_A11Y) Log.v(TAG, "ivnalidating root because cell was added.");
        mExploreByTouchHelper.invalidateRoot();
    }

    private void notifyPatternStarted() {
        sendAccessEvent(R.string.lockscreen_access_pattern_start);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternStart();
        }
    }

    private void notifyPatternDetected() {
        sendAccessEvent(R.string.lockscreen_access_pattern_detected);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternDetected(mPattern);
        }
    }

    private void notifyPatternCleared() {
        sendAccessEvent(R.string.lockscreen_access_pattern_cleared);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCleared();
        }
    }

    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        // Dispatch to onHoverEvent first so mPatternInProgress is up to date when the
        // helper gets the event.
        boolean handled = super.dispatchHoverEvent(event);
        handled |= mExploreByTouchHelper.dispatchHoverEvent(event);
        return handled;
    }

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        mPatternDisplayMode = DisplayMode.Correct;
        invalidate();
    }

    /**
     * Clear the pattern lookup table.
     */
    private void clearPatternDrawLookup() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }

    /**
     * Disable input (for instance when displaying a message that will
     * timeout so user doesn't get view into messy state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - mPaddingLeft - mPaddingRight;
        mSquareWidth = width / 3.0f;

        if (DEBUG_A11Y) Log.v(TAG, "onSizeChanged(" + w + "," + h + ")");
        final int height = h - mPaddingTop - mPaddingBottom;
        mSquareHeight = height / 3.0f;
        mExploreByTouchHelper.invalidateRoot();
    }

    private int resolveMeasured(int measureSpec, int desired)
    {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.max(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);

        switch (mAspect) {
            case ASPECT_SQUARE:
                viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_WIDTH:
                viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_HEIGHT:
                viewWidth = Math.min(viewWidth, viewHeight);
                break;
        }
        // Log.v(TAG, "LockPatternView dimensions: " + viewWidth + "x" + viewHeight);
        setMeasuredDimension(viewWidth, viewHeight);
    }

    /**
     * Determines whether the point x, y will add a new point to the current
     * pattern (in addition to finding the cell, also makes heuristic choices
     * such as filling in gaps based on current pattern).
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    private Cell detectAndAddHit(float x, float y) {
        final Cell cell = checkForNewHit(x, y);
        if (cell != null) {

            // check for gaps in existing pattern
            Cell fillInGapCell = null;
            final ArrayList<Cell> pattern = mPattern;
            if (!pattern.isEmpty()) {
                final Cell lastCell = pattern.get(pattern.size() - 1);
                int dRow = cell.row - lastCell.row;
                int dColumn = cell.column - lastCell.column;

                int fillInRow = lastCell.row;
                int fillInColumn = lastCell.column;

                if(Math.abs(dRow) == 1 && Math.abs(dColumn) == 1 && cell.getValue() != 4
                        && lastCell.getValue() != 4){
                    if(cell.row == 0 || cell.row == 2){
                        fillInRow = cell.row;
                        fillInColumn = lastCell.column;
                    }else if(lastCell.row == 0 || lastCell.row == 2){
                        fillInRow = lastCell.row;
                        fillInColumn = cell.column;
                    }
                }else{
                    if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
                        fillInRow = lastCell.row + ((dRow > 0) ? 1 : -1);
                    }
                    if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
                        fillInColumn = lastCell.column + ((dColumn > 0) ? 1 : -1);
                    }
                }

                fillInGapCell = Cell.of(fillInRow, fillInColumn);
            }
            if (fillInGapCell != null &&
                    !mPatternDrawLookup[fillInGapCell.row][fillInGapCell.column]) {
                addCellToPattern(fillInGapCell);
            }
            addCellToPattern(cell);
            if (mEnableHapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            return cell;
        }
        return null;
    }

    private void addCellToPattern(Cell newCell) {
        mPatternDrawLookup[newCell.getRow()][newCell.getColumn()] = true;
        mPattern.add(newCell);
        if (!mInStealthMode) {
            if(BITMAP_LOCK){
                startCellBitmapAnimation(newCell);
            }else{
                startCellActivatedAnimation(newCell);
            }
        }
        notifyCellAdded();
    }

    private void startCellBitmapAnimation(final Cell cell){
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(1.0f, 1.2f, 1.0f);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float scale = (float) animation.getAnimatedValue();
                cell.setScale(scale);
                invalidate();
            }
        });
        valueAnimator.setDuration(192);
        valueAnimator.start();
    }

    private void startCellActivatedAnimation(Cell cell) {
        final CellState cellState = mCellStates[cell.row][cell.column];
        startRadiusAnimation(mDotSize/2, mDotSizeActivated/2, 96, mLinearOutSlowInInterpolator,
                cellState, new Runnable() {
                    @Override
                    public void run() {
                        startRadiusAnimation(mDotSizeActivated/2, mDotSize/2, 192,
                                mFastOutSlowInInterpolator,
                                cellState, null);
                    }
                });
        startLineEndAnimation(cellState, mInProgressX, mInProgressY,
                getCenterXForColumn(cell.column), getCenterYForRow(cell.row));
    }

    private void startLineEndAnimation(final CellState state,
            final float startX, final float startY, final float targetX, final float targetY) {
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state.lineAnimator = null;
            }
        });
        valueAnimator.setInterpolator(mFastOutSlowInInterpolator);
        valueAnimator.setDuration(100);
        valueAnimator.start();
        state.lineAnimator = valueAnimator;
    }

    private void startRadiusAnimation(float start, float end, long duration,
            Interpolator interpolator, final CellState state, final Runnable endRunnable) {
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(start, end);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                state.radius = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        if (endRunnable != null) {
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    endRunnable.run();
                }
            });
        }
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }

    // helper method to find which cell a point maps to
    private Cell checkForNewHit(float x, float y) {
        int rowHit,columnHit;
        if(BITMAP_LOCK){
            final int[] cellHit = getCellHit(x, y);
            if(cellHit == null){
                return null;
            }
            rowHit = cellHit[0];
            columnHit = cellHit[1];

        }else{
            rowHit = getRowHit(y);
            if (rowHit < 0) {
                return null;
            }
            columnHit = getColumnHit(x);
            if (columnHit < 0) {
                return null;
            }
        }
        if (mPatternDrawLookup[rowHit][columnHit]) {
            return null;
        }
        return Cell.of(rowHit, columnHit);
    }

    private int[] getCellHit(float x, float y) {
        if(mPoints == null) return null;
        x -= center;
        y -= center;
        for(int i=0; i<mPoints.length; i++){
            Point point = mPoints[i];
            if(point != null){
                if(Math.abs(point.x - x) <= 50 && Math.abs(point.y - y) <= 50){
                    int[] row_col = {point.row,point.col};
                    return row_col;
                }
            }
        }
        return null;
    }


    /**
     * Helper method to find the row that y falls into.
     * @param y The y coordinate
     * @return The row that y falls in, or -1 if it falls in no row.
     */
    private int getRowHit(float y) {
        final float squareHeight = mSquareHeight;
        float hitSize = squareHeight * mHitFactor;

        float offset = mPaddingTop + (squareHeight - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {

            final float hitTop = offset + squareHeight * i;
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Helper method to find the column x fallis into.
     * @param x The x coordinate.
     * @return The column that x falls in, or -1 if it falls in no column.
     */
    private int getColumnHit(float x) {
        final float squareWidth = mSquareWidth;
        float hitSize = squareWidth * mHitFactor;

        float offset = mPaddingLeft + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {

            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (mAccessibilityManager.isTouchExplorationEnabled()) {
            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    event.setAction(MotionEvent.ACTION_DOWN);
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    event.setAction(MotionEvent.ACTION_MOVE);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    event.setAction(MotionEvent.ACTION_UP);
                    break;
            }
            onTouchEvent(event);
            event.setAction(action);
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(event);
                return true;
            case MotionEvent.ACTION_UP:
                handleActionUp();
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mPatternInProgress) {
                    setPatternInProgress(false);
                    resetPattern();
                    notifyPatternCleared();
                }
                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
        }
        return false;
    }

    private void setPatternInProgress(boolean progress) {
        mPatternInProgress = progress;
        mExploreByTouchHelper.invalidateRoot();
    }

    private void handleActionMove(MotionEvent event) {
        // Handle all recent motion events so we don't skip any cells even when the device
        // is busy...
        final float radius = mPathWidth;
        final int historySize = event.getHistorySize();
        mTmpInvalidateRect.setEmpty();
        boolean invalidateNow = false;
        for (int i = 0; i < historySize + 1; i++) {
            final float x = i < historySize ? event.getHistoricalX(i) : event.getX();
            final float y = i < historySize ? event.getHistoricalY(i) : event.getY();
            Cell hitCell = detectAndAddHit(x, y);
            final int patternSize = mPattern.size();
            if (hitCell != null && patternSize == 1) {
                setPatternInProgress(true);
                notifyPatternStarted();
            }
            // note current x and y for rubber banding of in progress patterns
            final float dx = Math.abs(x - mInProgressX);
            final float dy = Math.abs(y - mInProgressY);
            if (dx > DRAG_THRESHHOLD || dy > DRAG_THRESHHOLD) {
                invalidateNow = true;
            }

            if (mPatternInProgress && patternSize > 0) {
                final ArrayList<Cell> pattern = mPattern;
                final Cell lastCell = pattern.get(patternSize - 1);
                float lastCellCenterX = getCenterXForColumn(lastCell.column);
                float lastCellCenterY = getCenterYForRow(lastCell.row);

                // Adjust for drawn segment from last cell to (x,y). Radius accounts for line width.
                float left = Math.min(lastCellCenterX, x) - radius;
                float right = Math.max(lastCellCenterX, x) + radius;
                float top = Math.min(lastCellCenterY, y) - radius;
                float bottom = Math.max(lastCellCenterY, y) + radius;

                // Invalidate between the pattern's new cell and the pattern's previous cell
                if (hitCell != null) {
                    final float width = mSquareWidth * 0.5f;
                    final float height = mSquareHeight * 0.5f;
                    final float hitCellCenterX = getCenterXForColumn(hitCell.column);
                    final float hitCellCenterY = getCenterYForRow(hitCell.row);

                    left = Math.min(hitCellCenterX - width, left);
                    right = Math.max(hitCellCenterX + width, right);
                    top = Math.min(hitCellCenterY - height, top);
                    bottom = Math.max(hitCellCenterY + height, bottom);
                }

                // Invalidate between the pattern's last cell and the previous location
                mTmpInvalidateRect.union(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom));
            }
        }
        mInProgressX = event.getX();
        mInProgressY = event.getY();

        // To save updates, we only invalidate if the user moved beyond a certain amount.
        if (invalidateNow) {
            mInvalidate.union(mTmpInvalidateRect);
            invalidate(mInvalidate);
            mInvalidate.set(mTmpInvalidateRect);
        }
    }

    private void sendAccessEvent(int resId) {
        announceForAccessibility(mContext.getString(resId));
    }

    private void handleActionUp() {
        // report pattern detected
        if (!mPattern.isEmpty()) {
            setPatternInProgress(false);
            cancelLineAnimations();
            notifyPatternDetected();
            invalidate();
        }
        if (PROFILE_DRAWING) {
            if (mDrawingProfilingStarted) {
                Debug.stopMethodTracing();
                mDrawingProfilingStarted = false;
            }
        }
    }

    private void cancelLineAnimations() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                CellState state = mCellStates[i][j];
                if (state.lineAnimator != null) {
                    state.lineAnimator.cancel();
                }
            }
        }
    }
    private void handleActionDown(MotionEvent event) {
        resetPattern();
        final float x = event.getX();
        final float y = event.getY();
        final Cell hitCell = detectAndAddHit(x, y);
        if (hitCell != null) {
            setPatternInProgress(true);
            mPatternDisplayMode = DisplayMode.Correct;
            notifyPatternStarted();
        } else if (mPatternInProgress) {
            setPatternInProgress(false);
            notifyPatternCleared();
        }
        if (hitCell != null) {
            final float startX = getCenterXForColumn(hitCell.column);
            final float startY = getCenterYForRow(hitCell.row);

            final float widthOffset = mSquareWidth / 2f;
            final float heightOffset = mSquareHeight / 2f;

            invalidate((int) (startX - widthOffset), (int) (startY - heightOffset),
                    (int) (startX + widthOffset), (int) (startY + heightOffset));
        }
        mInProgressX = x;
        mInProgressY = y;
        if (PROFILE_DRAWING) {
            if (!mDrawingProfilingStarted) {
                Debug.startMethodTracing("LockPatternDrawing");
                mDrawingProfilingStarted = true;
            }
        }
    }

    private float getCenterXForColumn(int column) {
        return mPaddingLeft + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return mPaddingTop + row * mSquareHeight + mSquareHeight / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {

        int width = this.getWidth();
        if(BITMAP_LOCK){
            center = width/2;
            // move origin to the center of the canvas
            canvas.translate(center, center);

            POINT_RADIUS = 20.0f;
            RADIUS = center - RD;

            float radius = RADIUS-SPACING;
            mOval = new RectF( -radius, -radius, radius, radius);

            float cx = (float)(RADIUS*Math.cos(45 * Math.PI / 180));
            float cy = (float)(RADIUS*Math.sin(45 * Math.PI / 180));

            /*
            * 4 is center of the canvas (0, 0)
            *
            *             1

            *       0           2

            *  3          4          5

            *       6           8

            *             7
            */
            Point[] points = {new Point(-cx, -cy)/* 0 */,new Point(0, -RADIUS)/* 1 */,new Point(cx, -cy)/* 2 */,
                    new Point(-RADIUS, 0)/* 3 */,new Point(0, 0)/* 4 */,new Point(RADIUS, 0)/* 5 */,
                    new Point(-cx, cy)/* 6 */,new Point(0, RADIUS)/* 7 */,new Point(cx, cy)/* 8 */};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    int index = i*3 + j;
                    points[index].row = i;
                    points[index].col = j;
                }
            }
            mPoints = points;

            // dotted line
            mPaint.setPathEffect (new DashPathEffect (new float[]{15, 18}, 0)) ;
            // hollow
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(Color.parseColor("#444444"));
            // dotted line Circle
            canvas.drawCircle(0, 0, RADIUS+SPACING, mPaint);

            // 0 -> 5 -> 6 -> 1 -> 8 -> 3 -> 2 -> 7 -> 0 -> 8
            int[] p1 = {5,6,1,8,3,2,7,0,8};
            Path path = new Path();
            path.moveTo(mPoints[0].x, mPoints[0].y);
            for(int i=0; i<p1.length; i++){
                int pos = p1[i];
                path.lineTo(mPoints[pos].x, mPoints[pos].y);
            }
            canvas.drawPath(path, mPaint);

            // 1 -> 7
            path.reset();
            path.moveTo(mPoints[1].x, mPoints[1].y);
            path.lineTo(mPoints[7].x, mPoints[7].y);
            canvas.drawPath(path, mPaint);

            // 2 -> 6
            path.reset();
            path.moveTo(mPoints[2].x, mPoints[2].y);
            path.lineTo(mPoints[6].x, mPoints[6].y);
            canvas.drawPath(path, mPaint);

            // 3 -> 5
            path.reset();
            path.moveTo(mPoints[3].x, mPoints[3].y);
            path.lineTo(mPoints[5].x, mPoints[5].y);
            canvas.drawPath(path, mPaint);


            // full line
            mPaint.setPathEffect(null) ;
            // full line Circle
            canvas.drawCircle(0, 0, radius, mPaint);
        }else{
            POINT_RADIUS = RD;
        }

        final ArrayList<Cell> pattern = mPattern;
        final int count = pattern.size();
        final boolean[][] drawLookup = mPatternDrawLookup;

        if (mPatternDisplayMode == DisplayMode.Animate) {

            // figure out which circles to draw

            // + 1 so we pause on complete pattern
            final int oneCycle = (count + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            final int spotInCycle = (int) (SystemClock.elapsedRealtime() -
                    mAnimatingPeriodStart) % oneCycle;
            final int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;

            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                final Cell cell = pattern.get(i);
                drawLookup[cell.getRow()][cell.getColumn()] = true;
            }

            // figure out in progress portion of ghosting line

            final boolean needToUpdateInProgressPoint = numCircles > 0
                    && numCircles < count;

            if (needToUpdateInProgressPoint) {
                final float percentageOfNextCircle =
                        ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING)) /
                                MILLIS_PER_CIRCLE_ANIMATING;

                final Cell currentCell = pattern.get(numCircles - 1);
                final float centerX = getCenterXForColumn(currentCell.column);
                final float centerY = getCenterYForRow(currentCell.row);

                final Cell nextCell = pattern.get(numCircles);
                final float dx = percentageOfNextCircle *
                        (getCenterXForColumn(nextCell.column) - centerX);
                final float dy = percentageOfNextCircle *
                        (getCenterYForRow(nextCell.row) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            // TODO: Infinite loop here...
            invalidate();
        }

        final Path currentPath = mCurrentPath;
        currentPath.rewind();
        float radius = 0;

        // draw the circle-rings
        for (int i = 0; i < 3; i++) {
            mCurrentRingRow = i;
            float centerY = getCenterYForRow(i);
            for (int j = 0; j < 3; j++) {
                mCurrentRingColumn = j;
                CellState cellState = mCellStates[i][j];
                radius = cellState.radius + POINT_RADIUS;
                float centerX = getCenterXForColumn(j);
                float translationY = cellState.translationY;

                int index = i*3+j;
                if(BITMAP_LOCK){
                    Point point = mPoints[index];
                    centerX = point.x;
                    centerY = point.y;
                }

                if(BITMAP_LOCK){
                    drawCircle(canvas,centerX,centerY);
                    //drawCircleText(canvas, index+"", point.x, point.y);
                }else{
                    drawCircleRing(canvas, (int) centerX, (int) centerY + translationY,
                            radius, drawLookup[i][j], cellState.alpha);
                }
            }
        }

        // TODO: the path should be created and cached every time we hit-detect a cell
        // only the last segment of the path should be computed here
        // draw the path of the pattern (unless we are in stealth mode)
        final boolean drawPath = !mInStealthMode;

        if (drawPath) {
            boolean anyCircles = false;
            float lastX = 0f;
            float lastY = 0f;
            int lastIndex = -1;
            for (int i = 0; i < count; i++) {
                Cell cell = pattern.get(i);
                // path paint cell
                mCurrentRingRow = cell.row;
                mCurrentRingColumn = cell.column;
                int color = getCurrentColor(true /* partOfPattern */);
                mPathPaint.setColor(color);

                // only draw the part of the pattern stored in
                // the lookup table (this is only different in the case
                // of animation).
                if (!drawLookup[cell.row][cell.column]) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(cell.column);
                float centerY = getCenterYForRow(cell.row);

                int index = cell.getValue();;
                if(BITMAP_LOCK){
                    Point point = mPoints[cell.getValue()];
                    centerX = point.x;
                    centerY = point.y;
                }

                int sum = index + lastIndex;
                if (i != 0) {
                    if(BITMAP_LOCK){
                        int diff = Math.abs(index - lastIndex);
                        if(index == 4 || lastIndex == 4 || sum == 8 || sum == 5 || sum == 11 ||
                                (sum%2 != 0 && diff != 3 && diff != 1)){
                            //CellState state = mCellStates[cell.row][cell.column];
                            currentPath.rewind();
                            currentPath.moveTo(lastX, lastY);
                            currentPath.lineTo(centerX, centerY);
                            canvas.drawPath(currentPath, mPathPaint);
                        }else{
                            float startAngle = calculateAngle(lastX, lastY);
                            float endAngle = calculateAngle(centerX, centerY);
                            mPathPaint.setStyle(Paint.Style.STROKE);
                            float angle = endAngle - startAngle;
                            if(lastIndex == 2 && index == 5) angle = 45.f;
                            if(lastIndex == 1 && index == 5) angle = 90.f;
                            if(lastIndex == 2 && index == 8) angle = 90.f;
                            if(lastIndex == 5 && index == 2) angle = -45.f;
                            if(lastIndex == 5 && index == 1) angle = -90.f;
                            if(lastIndex == 8 && index == 2) angle = -90.f;
                            canvas.drawArc(mOval,startAngle,angle,false,mPathPaint);
                        }
                    }else{
                        if ((mLastRingColumn - mCurrentRingColumn)%2 == 0 && (mLastRingRow - mCurrentRingRow)%2 == 0) {
                            float between_x = (lastX + centerX)/2;
                            float between_y = (lastY + centerY)/2;
                            drawSingleLine(lastX, lastY, between_x, between_y, radius, currentPath, canvas);
                            drawSingleLine(between_x, between_y, centerX, centerY, radius, currentPath, canvas);
                        } else {
                            drawSingleLine(lastX, lastY, centerX, centerY, radius, currentPath, canvas);
                        }
                    }
                }
                // when drawpath, draw circle point
                if(BITMAP_LOCK){
                    lastIndex = index;
                }else{
                    drawCirclePoint(canvas, centerX, centerY);
                }

                lastX = centerX;
                lastY = centerY;

                mLastRingColumn = mCurrentRingColumn;
                mLastRingRow = mCurrentRingRow;
            }

            // last draw the bitmap circle
            if(BITMAP_LOCK){
                for (int i = 0; i < count; i++) {
                    Cell cell = pattern.get(i);
                    Point point = mPoints[cell.getValue()];
                    drawCircleBitmap(canvas, point.x, point.y, i, cell.getScale());
                }
            }


            // draw last in progress section
            if ((mPatternInProgress || mPatternDisplayMode == DisplayMode.Animate)
                    && anyCircles) {
                if(BITMAP_LOCK && mInProgressX != -1 && mInProgressY != -1){
                    float progressX = mInProgressX - center;
                    float progressY = mInProgressY - center;

                    currentPath.rewind();
                    currentPath.moveTo(lastX, lastY);

                    currentPath.lineTo(progressX, progressY);
                    mPathPaint.setAlpha((int) (calculateLastSegmentAlpha(
                            progressX, progressY, lastX, lastY) * 255f));
                    canvas.drawPath(currentPath, mPathPaint);
                }else{
                    drawSingleLine(lastX, lastY, mInProgressX, mInProgressY, radius, currentPath, canvas);
                }
            }
        }
    }

    private int mCurrentRingRow = -1;
    private int mCurrentRingColumn = -1;
    private int mLastRingRow = -1;
    private int mLastRingColumn = -1;

    // standard radius
    private float RD = 60.0f;
    // graph area radius
    private float RADIUS = 0.0f;
    // draw point radius
    private float POINT_RADIUS = 0.0f;
    private final float SPACING = 4.0f;
    // distance between the center of a circle and its boundary
    private float center = 0.0f;
    private Point[] mPoints;
    private RectF mOval;
    private int[] mPointBitmap;
    private int[] mPointBitmapError;

    // enable bitmap lock pattern
    private boolean BITMAP_LOCK = false;
    public void setBitmapLock(boolean enable){
        BITMAP_LOCK = enable;
        if(BITMAP_LOCK && mPointBitmap == null){
            final int[] pointBitmap = {R.drawable.ic_unlock_dot_0,R.drawable.ic_unlock_dot_1,
                    R.drawable.ic_unlock_dot_2,R.drawable.ic_unlock_dot_3,R.drawable.ic_unlock_dot_4,
                    R.drawable.ic_unlock_dot_5,R.drawable.ic_unlock_dot_6,R.drawable.ic_unlock_dot_7,
                    R.drawable.ic_unlock_dot_8};
            mPointBitmap = pointBitmap;
            final int[] pointBitmapError = {R.drawable.ic_unlock_dot_e,
                    R.drawable.ic_unlock_dot_e,R.drawable.ic_unlock_dot_e,
                    R.drawable.ic_unlock_dot_e,R.drawable.ic_unlock_dot_e,
                    R.drawable.ic_unlock_dot_e,R.drawable.ic_unlock_dot_e,
                    R.drawable.ic_unlock_dot_e,R.drawable.ic_unlock_dot_e};
            mPointBitmapError = pointBitmapError;
        }
    }

    class Point{
        public Point(float x, float y){
            this.x = x;
            this.y = y;
        }
        float x,y;
        int row,col;
    }

    private float calculateAngle(float x, float y){
        double cosx = x/Math.sqrt(x*x+y*y);
        double arc = Math.acos(cosx);
        float angle = (float) (arc*180/Math.PI);
        if(y < 0){
            return 360.f - angle;
        }
        return angle;
    }

    private void drawCircle(Canvas canvas, float x, float y){
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, POINT_RADIUS, mPaint);
    }

    private void drawCircleText(Canvas canvas, String text, float x, float y){
        mPaint.setColor(Color.BLUE);
        mPaint.setTextSize(50);
        canvas.drawText(text, x-15, y+15, mPaint);
    }

    private void drawCircleBitmap(Canvas canvas, float centerX, float centerY, int index, float scale) {
        Bitmap tmp = null;
        if (mPatternDisplayMode == DisplayMode.Wrong){
            tmp = BitmapFactory.decodeResource(getResources(), mPointBitmapError[index]);
        }else{
            tmp = BitmapFactory.decodeResource(getResources(), mPointBitmap[index]);
        }
        Log.e("SHUIYES","w: "+center);
        int width = Math.round((center/3)*scale);
        Bitmap bitmap = Bitmap.createScaledBitmap(tmp, width, width, true);;
        canvas.drawBitmap(bitmap, centerX-bitmap.getWidth()/2, centerY-bitmap.getHeight()/2, mPathPaint);
        tmp.recycle();
        bitmap.recycle();
    }

    private void drawSingleLine(float fromX, float fromY, float endX, float endY, float radius, Path currentPath, Canvas canvas) {
        float a = fromX-endX;
        float b = fromY-endY;
        float c = (float)Math.sqrt(a*a + b*b);
        float cos_ = a/c;
        float sin_ = b/c;
        float from_x = fromX - radius*cos_;
        float from_y = fromY - radius*sin_;
        float end_x = endX + radius*cos_;
        float end_y = endY + radius*sin_;
        currentPath.rewind();
        currentPath.moveTo(from_x, from_y);
        currentPath.lineTo(end_x, end_y);

        int color = getCurrentColor(true);
        if(color == mRegularColor){
            LinearGradient lg = new LinearGradient(fromX, fromY, endX, endY,
                new int[]{mRegularColor, 0xff845f46}, null, Shader.TileMode.MIRROR);
            mPathPaint.setShader(lg);
        }else{
            mPathPaint.setColor(color);
            mPathPaint.setShader(null);
        }

        canvas.drawPath(currentPath, mPathPaint);
        // default no shader
        mPathPaint.setShader(null);
    }

    private float calculateLastSegmentAlpha(float x, float y, float lastX, float lastY) {
        float diffX = x - lastX;
        float diffY = y - lastY;
        float dist = (float) Math.sqrt(diffX*diffX + diffY*diffY);
        float frac = dist/mSquareWidth;
        return Math.min(1f, Math.max(0f, (frac - 0.3f) * 4f));
    }

    private int getCurrentColor(boolean partOfPattern) {
        if(mPattern.size() == 0 || !isPatternRing(mCurrentRingRow,mCurrentRingColumn)){
            return 0xffb3b3b3;
        } else if (!partOfPattern || mInStealthMode || mPatternInProgress) {
            // unselected circle
            return mRegularColor;
        } else if (mPatternDisplayMode == DisplayMode.Wrong) {
            // the pattern is wrong
            return mErrorColor;
        } else if (mPatternDisplayMode == DisplayMode.Correct ||
                mPatternDisplayMode == DisplayMode.Animate) {
            return mSuccessColor;
        } else {
            throw new IllegalStateException("unknown display mode " + mPatternDisplayMode);
        }
    }

    /**
    * The current ring is painted in the pattern
    */
    private boolean isPatternRing(int row, int col){
        boolean isPatternCircle = false;
        for(int i = 0; i < mPattern.size(); i++){
            Cell cell = mPattern.get(i);
            if(cell.row == row && cell.column == col){
                isPatternCircle = true;
                break;
            }
        }
        return isPatternCircle;
     }

    /**
     * @param partOfPattern Whether this circle is part of the pattern.
     */
    private void drawCircleRing(Canvas canvas, float centerX, float centerY, float radius,
            boolean partOfPattern, float alpha) {
        mPaint.setStyle(Paint.Style.STROKE);

        int color = getCurrentColor(partOfPattern);
        if(color == mRegularColor){
            RadialGradient rg = new RadialGradient(radius, radius, radius,
                    new int[] {color,0xff845f46},null, Shader.TileMode.MIRROR);
            mPaint.setShader(rg);
        }else{
            mPaint.setColor(color);
            mPaint.setShader(null);
        }
        canvas.drawCircle(centerX, centerY, radius, mPaint);
        // default no shader
        mPaint.setShader(null);
    }

    private void drawCirclePoint(Canvas canvas, float centerX, float centerY) {
        mPaint.setColor(getCurrentColor(true /* partOfPattern */));
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, mDotSize/2+5, mPaint);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState,
                LockPatternUtils.patternToString(mPattern),
                mPatternDisplayMode.ordinal(),
                mInputEnabled, mInStealthMode, mEnableHapticFeedback);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setPattern(
                DisplayMode.Correct,
                LockPatternUtils.stringToPattern(ss.getSerializedPattern()));
        mPatternDisplayMode = DisplayMode.values()[ss.getDisplayMode()];
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mEnableHapticFeedback = ss.isTactileFeedbackEnabled();
    }

    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final int mDisplayMode;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mTactileFeedbackEnabled;

        /**
         * Constructor called from {@link LockPatternView#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, String serializedPattern, int displayMode,
                boolean inputEnabled, boolean inStealthMode, boolean tactileFeedbackEnabled) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isTactileFeedbackEnabled(){
            return mTactileFeedbackEnabled;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }

        @SuppressWarnings({ "unused", "hiding" }) // Found using reflection
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private final class PatternExploreByTouchHelper extends ExploreByTouchHelper {
        private Rect mTempRect = new Rect();
        private HashMap<Integer, VirtualViewContainer> mItems = new HashMap<Integer,
                VirtualViewContainer>();

        class VirtualViewContainer {
            public VirtualViewContainer(CharSequence description) {
                this.description = description;
            }
            CharSequence description;
        };

        public PatternExploreByTouchHelper(View forView) {
            super(forView);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            // This must use the same hit logic for the screen to ensure consistency whether
            // accessibility is on or off.
            int id = getVirtualViewIdForHit(x, y);
            return id;
        }

        @Override
        protected void getVisibleVirtualViews(IntArray virtualViewIds) {
            if (DEBUG_A11Y) Log.v(TAG, "getVisibleVirtualViews(len=" + virtualViewIds.size() + ")");
            if (!mPatternInProgress) {
                return;
            }
            for (int i = VIRTUAL_BASE_VIEW_ID; i < VIRTUAL_BASE_VIEW_ID + 9; i++) {
                if (!mItems.containsKey(i)) {
                    VirtualViewContainer item = new VirtualViewContainer(getTextForVirtualView(i));
                    mItems.put(i, item);
                }
                // Add all views. As views are added to the pattern, we remove them
                // from notification by making them non-clickable below.
                virtualViewIds.add(i);
            }
        }

        @Override
        protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
            if (DEBUG_A11Y) Log.v(TAG, "onPopulateEventForVirtualView(" + virtualViewId + ")");
            // Announce this view
            if (mItems.containsKey(virtualViewId)) {
                CharSequence contentDescription = mItems.get(virtualViewId).description;
                event.getText().add(contentDescription);
            }
        }

        @Override
        public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onPopulateAccessibilityEvent(host, event);
            if (!mPatternInProgress) {
                CharSequence contentDescription = getContext().getText(
                        R.string.lockscreen_access_pattern_area);
                event.setContentDescription(contentDescription);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfo node) {
            if (DEBUG_A11Y) Log.v(TAG, "onPopulateNodeForVirtualView(view=" + virtualViewId + ")");

            // Node and event text and content descriptions are usually
            // identical, so we'll use the exact same string as before.
            node.setText(getTextForVirtualView(virtualViewId));
            node.setContentDescription(getTextForVirtualView(virtualViewId));

            if (mPatternInProgress) {
                node.setFocusable(true);

                if (isClickable(virtualViewId)) {
                    // Mark this node of interest by making it clickable.
                    node.addAction(AccessibilityAction.ACTION_CLICK);
                    node.setClickable(isClickable(virtualViewId));
                }
            }

            // Compute bounds for this object
            final Rect bounds = getBoundsForVirtualView(virtualViewId);
            if (DEBUG_A11Y) Log.v(TAG, "bounds:" + bounds.toString());
            node.setBoundsInParent(bounds);
        }

        private boolean isClickable(int virtualViewId) {
            // Dots are clickable if they're not part of the current pattern.
            if (virtualViewId != ExploreByTouchHelper.INVALID_ID) {
                int row = (virtualViewId - VIRTUAL_BASE_VIEW_ID) / 3;
                int col = (virtualViewId - VIRTUAL_BASE_VIEW_ID) % 3;
                return !mPatternDrawLookup[row][col];
            }
            return false;
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action,
                Bundle arguments) {
            if (DEBUG_A11Y) Log.v(TAG, "onPerformActionForVirtualView(id=" + virtualViewId
                    + ", action=" + action);
            switch (action) {
                case AccessibilityNodeInfo.ACTION_CLICK:
                    // Click handling should be consistent with
                    // onTouchEvent(). This ensures that the view works the
                    // same whether accessibility is turned on or off.
                    return onItemClicked(virtualViewId);
                default:
                    if (DEBUG_A11Y) Log.v(TAG, "*** action not handled in "
                            + "onPerformActionForVirtualView(viewId="
                            + virtualViewId + "action=" + action + ")");
            }
            return false;
        }

        boolean onItemClicked(int index) {
            if (DEBUG_A11Y) Log.v(TAG, "onItemClicked(" + index + ")");

            // Since the item's checked state is exposed to accessibility
            // services through its AccessibilityNodeInfo, we need to invalidate
            // the item's virtual view. At some point in the future, the
            // framework will obtain an updated version of the virtual view.
            invalidateVirtualView(index);

            // We need to let the framework know what type of event
            // happened. Accessibility services may use this event to provide
            // appropriate feedback to the user.
            sendEventForVirtualView(index, AccessibilityEvent.TYPE_VIEW_CLICKED);

            return true;
        }

        private Rect getBoundsForVirtualView(int virtualViewId) {
            int ordinal = virtualViewId - VIRTUAL_BASE_VIEW_ID;
            final Rect bounds = mTempRect;
            final int row = ordinal / 3;
            final int col = ordinal % 3;
            final CellState cell = mCellStates[row][col];
            float centerX = getCenterXForColumn(col);
            float centerY = getCenterYForRow(row);
            float cellheight = mSquareHeight * mHitFactor * 0.5f;
            float cellwidth = mSquareWidth * mHitFactor * 0.5f;
            bounds.left = (int) (centerX - cellwidth);
            bounds.right = (int) (centerX + cellwidth);
            bounds.top = (int) (centerY - cellheight);
            bounds.bottom = (int) (centerY + cellheight);
            return bounds;
        }


        private CharSequence getTextForVirtualView(int virtualViewId) {
            final Resources res = getResources();
            return res.getString(R.string.lockscreen_access_pattern_cell_added);
        }

        /**
         * Helper method to find which cell a point maps to
         *
         * if there's no hit.
         * @param x touch position x
         * @param y touch position y
         * @return VIRTUAL_BASE_VIEW_ID+id or 0 if no view was hit
         */
        private int getVirtualViewIdForHit(float x, float y) {
            final int rowHit = getRowHit(y);
            if (rowHit < 0) {
                return ExploreByTouchHelper.INVALID_ID;
            }
            final int columnHit = getColumnHit(x);
            if (columnHit < 0) {
                return ExploreByTouchHelper.INVALID_ID;
            }
            boolean dotAvailable = mPatternDrawLookup[rowHit][columnHit];
            int dotId = (rowHit * 3 + columnHit) + VIRTUAL_BASE_VIEW_ID;
            int view = dotAvailable ? dotId : ExploreByTouchHelper.INVALID_ID;
            if (DEBUG_A11Y) Log.v(TAG, "getVirtualViewIdForHit(" + x + "," + y + ") => "
                    + view + "avail =" + dotAvailable);
            return view;
        }
    }
}
