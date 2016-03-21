package com.android.launcher3;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 继承于FrameLayout，实现了两个接口
 * <p>1、ViewGroup.OnHierarchyChangeListener：用于监听ViewGroup中的View的层次变化</p>
 * <p>2、Insettable：暂时未知</p>
 */
public class InsettableFrameLayout extends FrameLayout implements
        ViewGroup.OnHierarchyChangeListener, Insettable {

    protected Rect mInsets = new Rect();

    public InsettableFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        //设置OnHierarchyChangeListener的监听
        setOnHierarchyChangeListener(this);
    }

    /**
     * 这里个方法是设置一个rect给child，或计算好child的margin再重新赋予给child
     *
     * @param child
     * @param newInsets
     * @param oldInsets
     */
    public void setFrameLayoutChildInsets(View child, Rect newInsets, Rect oldInsets) {
        //获取child的布局参数
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();

        //判断child是否实现了Insettable的接口
        if (child instanceof Insettable) {
            //如果是，则将rect类型的东西设置给child
            ((Insettable) child).setInsets(newInsets);
        } else if (!lp.ignoreInsets) {
            //如果不是，而且这个忽略inset的标志位为false
            lp.topMargin += (newInsets.top - oldInsets.top);
            lp.leftMargin += (newInsets.left - oldInsets.left);
            lp.rightMargin += (newInsets.right - oldInsets.right);
            lp.bottomMargin += (newInsets.bottom - oldInsets.bottom);
        }
        //把修改过的布局参数重新设置给child
        child.setLayoutParams(lp);
    }

    /**
     * <p>里面调用了setFrameLayoutChildInsets的方法，猜测mInsets是子类继承后对其有操作</p>
     * 而insets则是另外设置的，结果都是父布局设置rect给他包含的childs
     *
     * @param insets
     */
    @Override
    public void setInsets(Rect insets) {
        final int n = getChildCount();
        for (int i = 0; i < n; i++) {
            final View child = getChildAt(i);
            setFrameLayoutChildInsets(child, insets, mInsets);
        }
        mInsets.set(insets);
    }

    /**
     * 重写这个方法，指定这个ViewGroup的LayoutParams
     * @param attrs
     * @return
     */
    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new InsettableFrameLayout.LayoutParams(getContext(), attrs);
    }

    /**
     * 指定默认的布局params，其中的布局长宽为WRAP_CONTENT
     * @return
     */
    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    /**
     * 判断布局参数是否相同
     * @param p
     * @return
     */
    // Override to allow type-checking of LayoutParams.
    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof InsettableFrameLayout.LayoutParams;
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    /**
     * 适配InsettableFrameLayout的LayoutParams
     * 在里面获取是否忽略insets的标志位
     */
    public static class LayoutParams extends FrameLayout.LayoutParams {
        boolean ignoreInsets = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.InsettableFrameLayout_Layout);
            ignoreInsets = a.getBoolean(
                    R.styleable.InsettableFrameLayout_Layout_layout_ignoreInsets, false);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams lp) {
            super(lp);
        }
    }

    /**
     * 监听该ViewGroup中的child是否有增加
     *
     * @param parent
     * @param child
     */
    @Override
    public void onChildViewAdded(View parent, View child) {
        setFrameLayoutChildInsets(child, mInsets, new Rect());
    }

    /**
     * 监听该ViewGroup中的child是否被移除（这里没有作任何处理）
     *
     * @param parent
     * @param child
     */
    @Override
    public void onChildViewRemoved(View parent, View child) {
    }

}
