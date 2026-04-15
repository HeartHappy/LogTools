package com.hearthappy.loggerx.preview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView

class ViewPagerCompatibleScrollView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private var lastX = 0f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.x
                // 默认不允许父布局拦截，确保当前 View 能收到完整事件
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = ev.x
                val deltaX = currentX - lastX

                // direction < 0 为向右滑动（检查左边界），direction > 0 为向左滑动（检查右边界）
                val direction = if (deltaX > 0) -1 else 1

                // 如果当前 View 无法在滑动方向上继续滚动，则允许 ViewPager2 拦截
                if (!canScrollHorizontally(direction)) {
                    parent.requestDisallowInterceptTouchEvent(false)
                } else {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}