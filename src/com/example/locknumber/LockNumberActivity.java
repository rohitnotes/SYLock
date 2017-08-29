package com.example.locknumber;

import com.android.internal.widget.custom.LockNumberView;
import com.android.internal.widget.custom.LockPatternUtils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

public class LockNumberActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_number);

        mLockNumberView = (LockNumberView) this.findViewById(R.id.lockNumber);
        mLockNumberView.setOnPasswdChangeListner(mPasswdChangeListner);
        mLockNumberView.setNoticeColor(Color.parseColor(this.getString(R.string.num_text_color)));

        mBack = (ImageView) this.findViewById(R.id.back);
        mBack.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        updateStage(Stage.New);
    }

    protected LockNumberView mLockNumberView;
    private ImageView mBack;

    private String mFirstPasswd = null;

    protected LockNumberView.onPasswdChangeListner mPasswdChangeListner =
            new LockNumberView.onPasswdChangeListner() {

                public void onPasswdCleared(){
                    //ignore
                }

                public void onPasswdChanged(String passwd){
                    //ignore
                }

                public void onPasswdCompleted(String passwd){
                    //android.widget.Toast.makeText(getContext(), "密码："+passwd, 0).show();
                    switch(mUiStage){
                        case New:
                            mFirstPasswd = passwd;
                            updateStage(Stage.Confirm);
                            break;
                        case Confirm:
                            if(passwd.equals(mFirstPasswd)){
                                updateStage(Stage.Success);
                                savePasswdAndFinish();
                            }else{
                                updateStage(Stage.Error);
                            }
                            break;
                        default:
                            break;
                    }

                    mLockNumberView.postDelayed(mResetRunnable, 100);
                }

            };



    private Runnable mResetRunnable = new Runnable() {
        public void run() {
            mLockNumberView.clearPasswd();
        }
    };

    private Stage mUiStage = Stage.New;
    private Stage mPreviousStage = mUiStage;
    protected enum Stage {

        New(R.string.num_input_passwd, true),
        Confirm(R.string.num_confirm_passwd, true),
        Success(R.string.num_success, false),
        Error(R.string.num_passwd_confirm_wrong, false);
        Stage(int msgId, boolean enable){
            this.msgId = msgId;
            this.enable = enable;
        }
        final int msgId;
        final boolean enable;
    }

    protected void updateStage(Stage stage) {
        mLockNumberView.setChangeEnable(stage.enable);
        mLockNumberView.setNotice(stage.msgId);

        mPreviousStage = mUiStage;
        switch(stage){
            case New:
                mBack.setVisibility(View.VISIBLE);
                break;
            case Confirm:
                mBack.setVisibility(View.GONE);
                break;
            case Success:
                break;
            case Error:
                mLockNumberView.removeCallbacks(mClearPatternRunnable);
                mLockNumberView.postDelayed(mClearPatternRunnable, 500);
                break;
        }
        mUiStage = stage;
    }

    private Runnable mClearPatternRunnable = new Runnable() {
        public void run() {
            //updateStage(mPreviousStage);
            updateStage(Stage.New);
        }
    };

    private void savePasswdAndFinish() {
        Log.d("SHUIYES", "save passwd="+mFirstPasswd);

        setResult(RESULT_OK);
        finish();
    }

}
