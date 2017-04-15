package com.gxz.example.videoedit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class RangeSeekBar2<T extends Number> extends ImageView {
    private static final String TAG = RangeSeekBar.class.getSimpleName();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint rectPaint;
    private Bitmap thumbImage_left;
    private Bitmap thumbImage_right;
    private Bitmap thumbPressedImage;
    private int thumbWidth;
    private float thumbHalfWidth;
    private float thumbHalfHeight;
    private final float thumbPaddingTop = 0;
    private final float thumbPressPaddingTop = 0;
    private final float padding = 0;
    private final T absoluteMinValue, absoluteMaxValue;
    private final NumberType numberType;
    private final double absoluteMinValuePrim, absoluteMaxValuePrim;
    private double normalizedMinValue = 0d;
    private double normalizedMaxValue = 1d;// normalized：规格化的--点坐标占总长度的比例值，范围从0-1
    private double normalizedMinValueTime = 0d;
    private double normalizedMaxValueTime = 1d;// normalized：规格化的--点坐标占总长度的比例值，范围从0-1
    private Thumb pressedThumb = null;
    private boolean notifyWhileDragging = false;
    private OnRangeSeekBarChangeListener<T> listener;
    private double min_width = 1;//最小裁剪距离
    private long min_cut_time = 2000;
    private boolean isMin;
    public static final int FILE_NOT_EXIST_ACTION = 144;
    public static final int INVALID_POINTER_ID = 255;
    public static final int ACTION_POINTER_INDEX_MASK = 0x0000ff00,
            ACTION_POINTER_INDEX_SHIFT = 8;
    private float mDownMotionX;// 记录touchEvent按下时的X坐标
    private int mActivePointerId = INVALID_POINTER_ID;
    private int mScaledTouchSlop;
    private boolean mIsDragging;
    private String videoPath;
    private boolean isTouchDown;
    private Bitmap m_bg;
    private Bitmap m_progress;


    public RangeSeekBar2(Context context,T absoluteMinValue, T absoluteMaxValue) throws IllegalArgumentException {
        super(context);
        this.absoluteMinValue = absoluteMinValue;
        this.absoluteMaxValue = absoluteMaxValue;
        absoluteMinValuePrim = absoluteMinValue.doubleValue();// 都转换为double类型的值
        absoluteMaxValuePrim = absoluteMaxValue.doubleValue();
        numberType = NumberType.fromNumber(absoluteMinValue);// 得到输入数字的枚举类型
        setFocusable(true);
        setFocusableInTouchMode(true);
        init();
    }

    private void init() {
        // 被认为是触摸滑动的最短距离
        mScaledTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        thumbImage_left = BitmapFactory.decodeResource(getResources(), R.drawable.handle_left);
        int width = thumbImage_left.getWidth();
        int height = thumbImage_left.getHeight();
        int newWidth = UIUtil.dip2px(getContext(),11);
        int newHeight = UIUtil.dip2px(getContext(),55);
        float scaleWidth = newWidth*1.0f/ width;
        float scaleHeight = newHeight*1.0f / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        thumbImage_left= Bitmap.createBitmap(thumbImage_left, 0, 0, width, height, matrix, true);

        thumbImage_right=thumbImage_left;
        thumbPressedImage=thumbImage_left;
//        thumbImage_right = BitmapFactory.decodeResource(getResources(), R.drawable.upload_cut_handle_r);
//        thumbPressedImage = BitmapFactory.decodeResource(getResources(), R.drawable.upload_cut_handle_pressed);
        thumbWidth = newWidth;
        thumbHalfWidth = thumbWidth/2;
        thumbHalfHeight = newHeight;
        rectPaint = getRectPaint();
        m_bg = BitmapFactory.decodeResource(getResources(), R.drawable.upload_overlay_black);
        m_progress = BitmapFactory.decodeResource(getResources(), R.drawable.upload_overlay_trans);
    }

    public void setMin_cut_time(long min_cut_time) {
        this.min_cut_time = min_cut_time;
    }

    public void setTouchDown(boolean touchDown) {
        isTouchDown = touchDown;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    /**
     * 供外部activity调用，控制是都在拖动的时候打印log信息，默认是false不打印
     */
    public boolean isNotifyWhileDragging() {
        return notifyWhileDragging;
    }


    public void setNotifyWhileDragging(boolean flag) {
        this.notifyWhileDragging = flag;
    }

    public T getAbsoluteMinValue() {
        return absoluteMinValue;
    }


    public T getAbsoluteMaxValue() {
        return absoluteMaxValue;
    }


    public T getSelectedMinValue() {
        return normalizedToValue(normalizedMinValueTime);
    }

    public void setSelectedMinValue(T value) {
        // in case absoluteMinValue == absoluteMaxValue, avoid division by zero
        // when normalizing.
        if (0 == (absoluteMaxValuePrim - absoluteMinValuePrim)) {
            // activity设置的最大值和最小值相等
            setNormalizedMinValue(0d);
        } else {
            setNormalizedMinValue(valueToNormalized(value));
        }
    }

    public T getSelectedMaxValue() {
        return normalizedToValue(normalizedMaxValueTime);
    }


    public void setSelectedMaxValue(T value) {
        // in case absoluteMinValue == absoluteMaxValue, avoid division by zero
        // when normalizing.
        if (0 == (absoluteMaxValuePrim - absoluteMinValuePrim)) {
            setNormalizedMaxValue(1d);
        } else {
            setNormalizedMaxValue(valueToNormalized(value));
        }
    }


    public void setOnRangeSeekBarChangeListener(OnRangeSeekBarChangeListener<T> listener) {
        this.listener = listener;
    }


    private boolean checkVideoFile(String file_path) {
        if (TextUtils.isEmpty(file_path)) {
            return true;
        }
        File check_src_file = new File(file_path);
        if (!check_src_file.exists()) {
            return false;
        }
        return true;
    }


    /**
     * ACTION_MASK在Android中是应用于多点触摸操作，字面上的意思大概是动作掩码的意思吧。
     * 在onTouchEvent(MotionEvent event)中，使用switch
     * (event.getAction())可以处理ACTION_DOWN和ACTION_UP事件；使用switch
     * (event.getAction() & MotionEvent.ACTION_MASK)
     * 就可以处理处理多点触摸的ACTION_POINTER_DOWN和ACTION_POINTER_UP事件。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (isTouchDown) {
            return super.onTouchEvent(event);
        }

        if (event.getPointerCount() > 1) {
            return super.onTouchEvent(event);
        }

        if (!isEnabled())
            return false;

        if (!checkVideoFile(videoPath)) {
            if (listener != null) {
                listener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue(), FILE_NOT_EXIST_ACTION, isMin, pressedThumb);
            }
            return super.onTouchEvent(event);
        }

        if (absoluteMaxValuePrim <= min_cut_time) {
            return super.onTouchEvent(event);
        }

        int pointerIndex;// 记录点击点的index

        final int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:

                // Remember where the motion event started
                // event.getPointerCount() -
                // 1得到最后一个点击屏幕的点，点击的点id从0到event.getPointerCount() - 1
                mActivePointerId = event.getPointerId(event.getPointerCount() - 1);
                pointerIndex = event.findPointerIndex(mActivePointerId);
                mDownMotionX = event.getX(pointerIndex);// 得到pointerIndex点击点的X坐标
                pressedThumb = evalPressedThumb(mDownMotionX);// 判断touch到的是最大值thumb还是最小值thumb

                // Only handle thumb presses.
                if (pressedThumb == null)
                    return super.onTouchEvent(event);

                setPressed(true);// 设置该控件被按下了
//                invalidate();// 通知执行onDraw方法
                onStartTrackingTouch();// 置mIsDragging为true，开始追踪touch事件

                trackTouchEvent(event);
                attemptClaimDrag();
                if (listener != null) {
                    listener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue(), MotionEvent.ACTION_DOWN, isMin, pressedThumb);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressedThumb != null) {

                    if (mIsDragging) {
                        trackTouchEvent(event);
                    } else {
                        // Scroll to follow the motion event
                        pointerIndex = event.findPointerIndex(mActivePointerId);
                        final float x = event.getX(pointerIndex);// 手指在控件上点的X坐标
                        // 手指没有点在最大最小值上，并且在控件上有滑动事件
                        if (Math.abs(x - mDownMotionX) > mScaledTouchSlop) {
                            setPressed(true);
                            Log.e(TAG, "没有拖住最大最小值");// 一直不会执行？
                            invalidate();
                            onStartTrackingTouch();
                            trackTouchEvent(event);
                            attemptClaimDrag();
                        }
                    }

                    if (notifyWhileDragging && listener != null) {
                        listener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue(), MotionEvent.ACTION_MOVE, isMin, pressedThumb);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsDragging) {
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                    setPressed(false);
                } else {
                    onStartTrackingTouch();
                    trackTouchEvent(event);
                    onStopTrackingTouch();
                }

                invalidate();
                if (listener != null) {
                    listener.onRangeSeekBarValuesChanged(this, getSelectedMinValue(), getSelectedMaxValue(), MotionEvent.ACTION_UP, isMin, pressedThumb);
                }
                pressedThumb = null;// 手指抬起，则置被touch到的thumb为空
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = event.getPointerCount() - 1;
                // final int index = ev.getActionIndex();
                mDownMotionX = event.getX(index);
                mActivePointerId = event.getPointerId(index);
                invalidate();
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(event);
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsDragging) {
                    onStopTrackingTouch();
                    setPressed(false);
                }
                invalidate(); // see above explanation
                break;
        }
        return true;
    }


    private final void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;

        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mDownMotionX = ev.getX(newPointerIndex);
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    /**
     * 一直追踪touch事件，刷新view
     *
     * @param event
     */
    private final void trackTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) return;
        Log.e(TAG, "trackTouchEvent: " + event.getAction() + " x: " + event.getX());
        final int pointerIndex = event.findPointerIndex(mActivePointerId);// 得到按下点的index
        float x = 0;
        try {
            x = event.getX(pointerIndex);//
        } catch (Exception e) {
            return;
        }

        if (Thumb.MIN.equals(pressedThumb)) {
            // screenToNormalized(x)-->得到规格化的0-1的值
            setNormalizedMinValue(screenToNormalized(x, 0));
        } else if (Thumb.MAX.equals(pressedThumb)) {
            setNormalizedMaxValue(screenToNormalized(x, 1));
        }
    }

    /**
     * 试图告诉父view不要拦截子控件的drag
     */
    private void attemptClaimDrag() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    void onStartTrackingTouch() {
        mIsDragging = true;
    }


    void onStopTrackingTouch() {
        mIsDragging = false;
    }


    @Override
    protected  void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 250;
        if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(widthMeasureSpec)) {
            width = MeasureSpec.getSize(widthMeasureSpec);
        }
        int height = 100;
        if (MeasureSpec.UNSPECIFIED != MeasureSpec.getMode(heightMeasureSpec)) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        }
        setMeasuredDimension(width, height);
    }


    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float bg_middle_left = 0;// 需要平铺的中间背景的开始坐标
        float bg_middle_right = getWidth();// 需要平铺的中间背景的开始坐标

        float scale = (bg_middle_right - bg_middle_left) / m_progress.getWidth();// 上层最大最小值间距离与m_progress比例
        float rangeL = normalizedToScreen(normalizedMinValue);
        float rangeR = normalizedToScreen(normalizedMaxValue);
        float pro_scale = (rangeR - rangeL) / m_progress.getWidth();// 上层最大最小值间距离与m_progress比例
        if (pro_scale > 0) {
            try {
                Matrix mx = new Matrix();
                mx.postScale(scale, 1f);
                Bitmap m_bg_new = Bitmap.createBitmap(m_bg, 0, 0, m_bg.getWidth(), m_progress.getHeight(), mx, true);

                Matrix pro_mx = new Matrix();
                pro_mx.postScale(pro_scale, 1f);
                Bitmap m_progress_new = Bitmap.createBitmap(m_progress, 0, 0, m_progress.getWidth(),
                        m_progress.getHeight(), pro_mx, true);

                canvas.drawBitmap(m_progress_new, rangeL, thumbPaddingTop, paint);
                Bitmap m_bg_new1 = Bitmap.createBitmap(m_bg_new, 0, 0, (int) (rangeL - bg_middle_left) + (int) thumbWidth / 2, m_progress.getHeight());
                canvas.drawBitmap(m_bg_new1, bg_middle_left, thumbPaddingTop, paint);

                Bitmap m_bg_new2 = Bitmap.createBitmap(m_bg_new, (int) (rangeR - thumbWidth / 2), 0, (int) (getWidth() - rangeR) + (int) thumbWidth / 2, m_progress.getHeight());
                canvas.drawBitmap(m_bg_new2, (int) (rangeR - thumbWidth / 2), thumbPaddingTop, paint);

                canvas.drawRect(rangeL, thumbPaddingTop, rangeR, thumbPaddingTop + UIUtil.dip2px(getContext(),2), rectPaint);
                canvas.drawRect(rangeL, getHeight() - UIUtil.dip2px(getContext(),2), rangeR, getHeight(), rectPaint);
            } catch (Exception e) {
                // 当pro_scale非常小，例如width=12，Height=48，pro_scale=0.01979065时，
                // 宽高按比例计算后值为0.237、0.949，系统强转为int型后宽就变成0了。就出现非法参数异常
                Log.e(TAG,
                        "IllegalArgumentException--width=" + m_progress.getWidth() + "Height=" + m_progress.getHeight()
                                + "pro_scale=" + pro_scale, e);
            }

        }
        // draw minimum thumb
        drawThumb(normalizedToScreen(normalizedMinValue), false, canvas, true);
        // draw maximum thumb
        float drawRight = normalizedToScreen(normalizedMaxValue);
//        if (drawRight>(getWidth()-thumbWidth)) {
//            drawRight = getWidth()-thumbWidth;
//        }
        drawThumb(drawRight, false, canvas, false);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Bundle bundle = new Bundle();
        bundle.putParcelable("SUPER", super.onSaveInstanceState());
        bundle.putDouble("MIN", normalizedMinValue);
        bundle.putDouble("MAX", normalizedMaxValue);
        bundle.putDouble("MIN_TIME", normalizedMinValueTime);
        bundle.putDouble("MAX_TIME", normalizedMaxValueTime);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcel) {
        final Bundle bundle = (Bundle) parcel;
        super.onRestoreInstanceState(bundle.getParcelable("SUPER"));
        normalizedMinValue = bundle.getDouble("MIN");
        normalizedMaxValue = bundle.getDouble("MAX");
        normalizedMinValueTime = bundle.getDouble("MIN_TIME");
        normalizedMaxValueTime = bundle.getDouble("MAX_TIME");
    }

    private void drawThumb(float screenCoord, boolean pressed, Canvas canvas, boolean isLeft) {
        canvas.drawBitmap(pressed ? thumbPressedImage : (isLeft ? thumbImage_left : thumbImage_right), screenCoord - (isLeft ? 0 : thumbWidth), (pressed ? thumbPressPaddingTop : thumbPaddingTop), paint);
    }

    private Paint getRectPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#ffffff"));
        return p;
    }

    /**
     * 重新运算求出参数的内容
     *
     * @param touchX touchX
     * @return 被touch的是空还是最大值或最小值
     */
    private Thumb evalPressedThumb(float touchX) {
        Thumb result = null;
        boolean minThumbPressed = isInThumbRange(touchX, normalizedMinValue, 2);// 触摸点是否在最小值图片范围内
        boolean maxThumbPressed = isInThumbRange(touchX, normalizedMaxValue, 2);
        if (minThumbPressed && maxThumbPressed) {
            // 如果两个thumbs重叠在一起，无法判断拖动哪个，做以下处理
            // 触摸点在屏幕右侧，则判断为touch到了最小值thumb，反之判断为touch到了最大值thumb
            result = (touchX / getWidth() > 0.5f) ? Thumb.MIN : Thumb.MAX;
        } else if (minThumbPressed) {
            result = Thumb.MIN;
        } else if (maxThumbPressed) {
            result = Thumb.MAX;
        }
        return result;
    }

    private boolean isInThumbRange(float touchX, double normalizedThumbValue, double scale) {
        // 当前触摸点X坐标-最小值图片中心点在屏幕的X坐标之差<=最小点图片的宽度的一般
        // 即判断触摸点是否在以最小值图片中心为原点，宽度一半为半径的圆内。
        return Math.abs(touchX - normalizedToScreen(normalizedThumbValue)) <= thumbHalfWidth * scale;
    }


    private boolean isInThumbRangeLeft(float touchX, double normalizedThumbValue, double scale) {
        // 当前触摸点X坐标-最小值图片中心点在屏幕的X坐标之差<=最小点图片的宽度的一般
        // 即判断触摸点是否在以最小值图片中心为原点，宽度一半为半径的圆内。
        return Math.abs(touchX - normalizedToScreen(normalizedThumbValue) - thumbWidth) <= thumbHalfWidth * scale;
    }


    public void setNormalizedMinValue(double value) {
        normalizedMinValue = Math.max(0d, Math.min(1d, Math.min(value, normalizedMaxValue)));
        invalidate();// 重新绘制此view
    }


    public void setNormalizedMaxValue(double value) {
        normalizedMaxValue = Math.max(0d, Math.min(1d, Math.max(value, normalizedMinValue)));
        invalidate();// 重新绘制此view
    }


    @SuppressWarnings("unchecked")
    private T normalizedToValue(double normalized) {
        return (T) numberType.toNumber(absoluteMinValuePrim + normalized
                * (absoluteMaxValuePrim - absoluteMinValuePrim));
    }

    private double valueToNormalized(T value) {
        if (0 == absoluteMaxValuePrim - absoluteMinValuePrim) {
            // prevent division by zero, simply return 0.
            return 0d;
        }
        return (value.doubleValue() - absoluteMinValuePrim) / (absoluteMaxValuePrim - absoluteMinValuePrim);
    }


    private float normalizedToScreen(double normalizedCoord) {
        // getWidth() - 2 * padding --> 整个View宽度减去左右padding，
        // 即减去一个thumb的宽度,即两个thumb可滑动的范围长度

        // normalizedCoord * (getWidth() - 2 * padding)
        // 规格化值与长度的成绩，即该点在屏幕上的相对x坐标值

        // padding + normalizedCoord * (getWidth() - 2 * padding)
        // 该点在屏幕上的绝对x坐标值

        return (float) (padding + normalizedCoord * (getWidth() - 2 * padding));
        // return (float) (normalizedCoord * getWidth());
    }


    private double screenToNormalized(float screenCoord, int position) {
        int width = getWidth();
        if (width <= 2 * padding) {
            // prevent division by zero, simply return 0.
            return 0d;
        } else {
            isMin = false;
            double current_width = screenCoord;
            float rangeL = normalizedToScreen(normalizedMinValue);
            float rangeR = normalizedToScreen(normalizedMaxValue);


            double min = min_cut_time / (absoluteMaxValuePrim - absoluteMinValuePrim) * (width - thumbWidth * 2);

//            if (absoluteMaxValuePrim>15*60*1000) {
//
//            } else {
//                min_width = Math.round(min+0.5d);
//            }
//            min_width = min;
            if (absoluteMaxValuePrim > 5 * 60 * 1000) {//大于5分钟的精确小数四位
                DecimalFormat df = new DecimalFormat("0.0000");
                min_width = Double.parseDouble(df.format(min));
            } else {
                min_width = Math.round(min + 0.5d);
            }
            if (position == 0) {
                if (isInThumbRangeLeft(screenCoord, normalizedMinValue, 0.5)) {
                    return normalizedMinValue;
                }

                float rightPosition = (getWidth() - rangeR) >= 0 ? (getWidth() - rangeR) : 0;
                double left_length = getValueLength() - (rightPosition + min_width);


                if (current_width > rangeL) {
                    current_width = rangeL + (current_width - rangeL);
                } else if (current_width <= rangeL) {
                    current_width = rangeL - (rangeL - current_width);
                }

                if (current_width > left_length) {
                    isMin = true;
                    current_width = left_length;
                }

                if (current_width < thumbWidth * 2 / 3) {
                    current_width = 0;
                }

                double resultTime = (current_width - padding) / (width - 2 * thumbWidth);
                normalizedMinValueTime = Math.min(1d, Math.max(0d, resultTime));
                double result = (current_width - padding) / (width - 2 * padding);
                return Math.min(1d, Math.max(0d, result));// 保证该该值为0-1之间，但是什么时候这个判断有用呢？
            } else {
                if (isInThumbRange(screenCoord, normalizedMaxValue, 0.5)) {
                    return normalizedMaxValue;
                }

                double right_length = getValueLength() - (rangeL + min_width);
                if (current_width > rangeR) {
                    current_width = rangeR + (current_width - rangeR);
                } else if (current_width <= rangeR) {
                    current_width = rangeR - (rangeR - current_width);
                }

                double paddingRight = getWidth() - current_width;

                if (paddingRight > right_length) {
                    isMin = true;
                    current_width = getWidth() - right_length;
                    paddingRight = right_length;
                }

                if (paddingRight < thumbWidth * 2 / 3) {
                    current_width = getWidth();
                    paddingRight = 0;
                }

                double resultTime = (paddingRight - padding) / (width - 2 * thumbWidth);
                resultTime = 1 - resultTime;
                normalizedMaxValueTime = Math.min(1d, Math.max(0d, resultTime));
                double result = (current_width - padding) / (width - 2 * padding);
                return Math.min(1d, Math.max(0d, result));// 保证该该值为0-1之间，但是什么时候这个判断有用呢？
            }

        }
    }


    private int getValueLength() {
        return (getWidth() - 2 * thumbWidth);
    }


    public interface OnRangeSeekBarChangeListener<T> {
        void onRangeSeekBarValuesChanged(RangeSeekBar2<?> bar, T minValue, T maxValue, int action, boolean isMin, Thumb pressedThumb);
    }

    /**
     * 只有两个值，一个代表滑动条上的最大值，一个代表滑动条上的最小值
     */
    public enum Thumb {
        MIN, MAX
    }

    private enum NumberType {
        LONG, DOUBLE, INTEGER, FLOAT, SHORT, BYTE, BIG_DECIMAL;

        public static <E extends Number> NumberType fromNumber(E value) throws IllegalArgumentException {
            if (value instanceof Long) {
                return LONG;
            }
            if (value instanceof Double) {
                return DOUBLE;
            }
            if (value instanceof Integer) {
                return INTEGER;
            }
            if (value instanceof Float) {
                return FLOAT;
            }
            if (value instanceof Short) {
                return SHORT;
            }
            if (value instanceof Byte) {
                return BYTE;
            }
            if (value instanceof BigDecimal) {
                return BIG_DECIMAL;
            }
            throw new IllegalArgumentException("Number class '" + value.getClass().getName() + "' is not supported");
        }

        public Number toNumber(double value) {
            // this代表调用该方法的对象，即枚举类中的枚举类型之一
            switch (this) {
                case LONG:
                    return new Long((long) value);
                case DOUBLE:
                    return value;
                case INTEGER:
                    return new Integer((int) value);
                case FLOAT:
                    return new Float(value);
                case SHORT:
                    return new Short((short) value);
                case BYTE:
                    return new Byte((byte) value);
                case BIG_DECIMAL:
                    return new BigDecimal(value);
            }
            throw new InstantiationError("can't convert " + this + " to a Number object");
        }
    }
}