package com.example.locknumber;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.widget.custom.LockPatternUtils;
import com.android.internal.widget.custom.LockPatternView;
import com.android.internal.widget.custom.LockPatternView.DisplayMode;

import java.util.ArrayList;
import java.util.List;

public class LockPatternActivity extends Activity implements View.OnClickListener {

    TextView mHeaderText;
    LockPatternView mLockPatternView;
    private ImageView mBack;

    protected List<LockPatternView.Cell> mChosenPattern = null;

    private Runnable mClearPatternRunnable = new Runnable() {
        public void run() {
            //updateStage(mPrevStage);
            updateStage(Stage.New);
        }
    };

    /**
     * Keep track internally of where the user is in choosing a pattern.
     */
    protected enum Stage {

        New(R.string.lockscreen_access_pattern_start, true),
        ChoiceTooShort(R.string.lockscreen_access_pattern_short, false),
        NeedToConfirm(R.string.lockscreen_access_pattern_confirm, true),
        ConfirmWrong(R.string.lockscreen_access_pattern_error, false),
        Success(R.string.lockscreen_access_pattern_detected, false);

        /**
         * @param headerMessage
         *            The message displayed at the top.
         * @param leftMode
         *            The mode of the left button.
         * @param rightMode
         *            The mode of the right button.
         * @param footerMessage
         *            The footer message.
         * @param patternEnabled
         *            Whether the pattern widget is enabled.
         */
        Stage(int headerMessage, boolean patternEnabled) {
            this.headerMessage = headerMessage;
            this.patternEnabled = patternEnabled;
        }

        final int headerMessage;
        final boolean patternEnabled;
    }

    private Stage mUiStage = Stage.New;
    private Stage mPrevStage = mUiStage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_pattern);

        mHeaderText = (TextView) findViewById(R.id.HeadText);
        mLockPatternView = (LockPatternView) findViewById(R.id.lockPattern);
        mLockPatternView.setOnPatternListener(mChooseNewLockPatternListener);
        mLockPatternView.setTactileFeedbackEnabled(true);
        if("bitmap".equals(getIntent().getStringExtra("lock_mode"))){
            mLockPatternView.setBitmapLock(true);
        }

        mBack = (ImageView) this.findViewById(R.id.back);
        mBack.setOnClickListener(this);
    }

    protected LockPatternView.OnPatternListener mChooseNewLockPatternListener =
            new LockPatternView.OnPatternListener() {
                @Override
                public void onPatternStart() {
                    mLockPatternView.removeCallbacks(mClearPatternRunnable);
                    patternInProgress();
                }

                @Override
                public void onPatternCleared() {
                    mLockPatternView.removeCallbacks(mClearPatternRunnable);
                }

                @Override
                public void onPatternCellAdded(List<LockPatternView.Cell> list) {

                }

                @Override
                public void onPatternDetected(List<LockPatternView.Cell> list) {
                    if (list == null)
                        return;

                    if (mUiStage == Stage.NeedToConfirm) {
                        if (mChosenPattern.equals(list)) {
                            updateStage(Stage.Success);
                        } else {
                            updateStage(Stage.ConfirmWrong);
                        }
                    } else if (mUiStage == Stage.New) {
                        if (list.size() < LockPatternUtils.MIN_LOCK_PATTERN_SIZE) {
                            updateStage(Stage.ChoiceTooShort);
                        } else {
                            mChosenPattern = new ArrayList<LockPatternView.Cell>(list);
                            updateStage(Stage.NeedToConfirm);
                        }
                    } else {
                        throw new IllegalStateException("Unexpected stage " + mUiStage
                                + " when " + "entering the pattern.");
                    }
                }

                private void patternInProgress() {
                    // ignore
                }
            };

    private void updateStage(Stage stage) {
        mPrevStage = mUiStage;

        mHeaderText.setText(stage.headerMessage);
        // same for whether the patten is enabled
        if (stage.patternEnabled) {
            mLockPatternView.enableInput();
        } else {
            mLockPatternView.disableInput();
        }

        mLockPatternView.setDisplayMode(DisplayMode.Correct);

        switch (stage) {
            case New:
                mBack.setVisibility(View.VISIBLE);
                mLockPatternView.clearPattern();
                break;
            case NeedToConfirm:
                mBack.setVisibility(View.GONE);
                mLockPatternView.clearPattern();
                break;
            case ChoiceTooShort:
                //ToastUtil.show(this, R.string.set_pattern_text_too_short, 0);
                mLockPatternView.clearPattern();
            case ConfirmWrong:
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                postClearPatternRunnable();
                break;
            case Success:
                saveChosenPatternAndFinish();
                break;
        }
        mUiStage = stage;
    }

    // clear the wrong pattern unless they have started a new one
    // already
    private void postClearPatternRunnable() {
        mLockPatternView.removeCallbacks(mClearPatternRunnable);
        mLockPatternView.postDelayed(mClearPatternRunnable, 1000);
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.back){
            finish();
        }
    }

    private void saveChosenPatternAndFinish() {
        Log.d("SHUIYES", "save pattern="+LockPatternUtils.patternToString(mChosenPattern));

        setResult(RESULT_OK);
        finish();
    }
}
