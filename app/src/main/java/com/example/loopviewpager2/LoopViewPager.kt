package com.example.loopviewpager2

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2021/7/7
 */
class LoopViewPager @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val viewPager2 = ViewPager2(context)

    init {
        addView(viewPager2, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                getLoopAdapterWrapper()?.stopLoop()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                getLoopAdapterWrapper()?.startLoop()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun getLoopAdapterWrapper(): MainActivity.LoopAdapterWrapper<*>? {
        return viewPager2.adapter as? MainActivity.LoopAdapterWrapper
    }

}