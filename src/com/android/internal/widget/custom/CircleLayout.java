package com.android.internal.widget.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class CircleLayout extends ViewGroup{

    private final int BTN_COUNT = 3;
    public CircleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();
        if (count < (BTN_COUNT+1)) return;

        int width = getMeasuredWidth()/2-getChildAt(BTN_COUNT).getMeasuredWidth()/2;
        int height = getMeasuredHeight()/2-getChildAt(BTN_COUNT).getMeasuredHeight()/2;
        int radius = (width<=height)?width:height;

        int degreeDelta = 360/(count-BTN_COUNT);

        final int parentLeft = getPaddingLeft();
        final int parentRight = right - left - getPaddingRight();
        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                width = child.getMeasuredWidth();
                height = child.getMeasuredHeight();
                int childLeft,childTop;
                // 删除按钮
                if (i == 0) {
                    childLeft = parentRight - width;
                    childTop = parentBottom - height;
                // 密码输入框
                }else if (i == 1) {
                    childLeft = parentLeft + (parentRight - parentLeft - width) / 2;
                    childTop = parentTop + (parentBottom - parentTop - height) / 2 ;
                // 提示
                }else if (i == 2) {
                    childLeft = parentLeft + (parentRight - parentLeft - width) / 2;
                    childTop = parentTop + (parentBottom - parentTop - height) / 2 - getChildAt(1).getMeasuredHeight();
                // 10个数字按钮
                }else{
                    childLeft = (int) (parentLeft + (parentRight - parentLeft - width) / 2-(radius * Math.sin(((i-BTN_COUNT)*degreeDelta)*Math.PI/180)));
                    childTop = (int) (parentTop + (parentBottom - parentTop - height) / 2-(radius * Math.cos(((i-BTN_COUNT)*degreeDelta)*Math.PI/180))) ;
                }
                child.layout(childLeft, childTop,childLeft+width,childTop+height);
            }
        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        int sizeWidth = MeasureSpec.getSize(widthMeasureSpec);
        int sizeHeight = MeasureSpec.getSize(heightMeasureSpec);

        measureChildren(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(sizeWidth, sizeHeight);
    }

}
