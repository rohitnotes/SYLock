package com.example.locknumber;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class LockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
    }

    public void clickImage(View v){
        Intent intent = new Intent();

        switch (v.getId()){
            case R.id.lock_number:
                intent.setClass(this, LockNumberActivity.class);
                break;
            case R.id.lock_bitmap:
                intent.putExtra("lock_mode", "bitmap");
            case R.id.lock_pattern:
                intent.setClass(this, LockPatternActivity.class);
                break;
        }
        this.startActivity(intent);
    }

}
