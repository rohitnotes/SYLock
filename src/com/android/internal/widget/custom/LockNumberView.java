package com.android.internal.widget.custom;

import com.example.locknumber.R;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class LockNumberView extends RelativeLayout implements View.OnClickListener{

    private final int MAX = 6;

    private Context mContext;
    private TextView mNoticeView;
    private TextView mPasswdView;
    private String mPasswd = "";
    private String mPointStr;

    public LockNumberView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;

        View view = LayoutInflater.from(context).inflate(R.layout.lock_number_view, this);
        mPasswdView = (TextView) view.findViewById(R.id.num_passwd);
        mNoticeView = (TextView) view.findViewById(R.id.num_notice);

        View delete = view.findViewById(R.id.num_delete);
        delete.setOnClickListener(this);
        View num0 = view.findViewById(R.id.num_0);
        num0.setOnClickListener(this);
        View num1 = view.findViewById(R.id.num_1);
        num1.setOnClickListener(this);
        View num2 = view.findViewById(R.id.num_2);
        num2.setOnClickListener(this);
        View num3 = view.findViewById(R.id.num_3);
        num3.setOnClickListener(this);
        View num4 = view.findViewById(R.id.num_4);
        num4.setOnClickListener(this);
        View num5 = view.findViewById(R.id.num_5);
        num5.setOnClickListener(this);
        View num6 = view.findViewById(R.id.num_6);
        num6.setOnClickListener(this);
        View num7 = view.findViewById(R.id.num_7);
        num7.setOnClickListener(this);
        View num8 = view.findViewById(R.id.num_8);
        num8.setOnClickListener(this);
        View num9 = view.findViewById(R.id.num_9);
        num9.setOnClickListener(this);

        mPointStr = context.getString(R.string.num_defalut_one_passwd);
    }

    public void clearPasswd(){
        mPasswd = "";
        changePasswd();
    }

    public void setNotice(String text){
        if(mNoticeView != null) mNoticeView.setText(text);
    }

    public void setNoticeColor(int color){
        if(mNoticeView != null){
            mNoticeView.setTextColor(color);
        }
    }

    public void setNotice(int id){
        if(mNoticeView != null) mNoticeView.setText(id);
    }

    private boolean mEnable = true;
    public void setChangeEnable(boolean enable){
        mEnable = enable;
    }

    @Override
    public void onClick(View v) {
        int len = mPasswd.length();
        if(v.getId() == R.id.num_delete){
            if(len > 0) mPasswd = mPasswd.substring(0, len-1);
        }else{
            if(!mEnable) return;
            if(len == MAX){
                if(mListner != null) mListner.onPasswdCompleted(mPasswd);
                return;
            }else{
                mPasswd += v.getContentDescription();
            }
        }
        changePasswd();
    }

    private Runnable mClearShowNum = new Runnable(){
        @Override
        public void run() {
            changePasswd();
        }
    };

    private void changePasswd(){
        int len = mPasswd.length();
        StringBuffer buf = new StringBuffer();
        buf.append("<font color="+mContext.getString(R.string.num_text_color)+">");
        for(int i = 0; i<=MAX; i++){
            if(len == 0){
                buf.append("</font>");
            }else if(i == (len-1)){
                if(mEnable){
                    mEnable = false;
                    if(i != 0) buf.append(" ");
                    buf.append(mPasswd.substring(len-1));
                    this.removeCallbacks(mClearShowNum);
                    this.postDelayed(mClearShowNum, 200);
                    continue;
                }else{
                    mEnable = true;
                }
            }else if(i == len){
                buf.append("</font>");
            }
            if(i != 0) buf.append(" ");
            if(i != MAX) buf.append(mPointStr);
        }
        mPasswdView.setText(Html.fromHtml(buf.toString()));

        if(len == 6 && mEnable){
            if(mListner != null) mListner.onPasswdCompleted(mPasswd);
        }else if(len == 0){
            if(mListner != null) mListner.onPasswdCleared();
        }else{
            if(mListner != null) mListner.onPasswdChanged(mPasswd);
        }

    }

    private onPasswdChangeListner mListner = null;
    public void setOnPasswdChangeListner(onPasswdChangeListner l){
        mListner = l;
    }
    public static interface onPasswdChangeListner{
        void onPasswdCleared();
        void onPasswdChanged(String passwd);
        void onPasswdCompleted(String passwd);
    }

}
