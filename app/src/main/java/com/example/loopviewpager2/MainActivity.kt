package com.example.loopviewpager2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.loopviewpager2.databinding.ActivityMainBinding
import com.example.loopviewpager2.databinding.LayoutLoopViewPagerItemBinding
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private val viewBinding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val realAdapter: RealAdapter by lazy {
        RealAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initView()
    }

    private fun initView() {
        val viewPager2 = viewBinding.lvp.viewPager2
        val tabLayout = viewBinding.tabLayout
        fillTabItems(tabLayout, realAdapter.itemCount)
        viewPager2.adapter = LoopAdapterWrapper(lifecycle, viewPager2, realAdapter).apply {
            //设置 item 点击监听
            onItemClick = { position ->
                Log.e("tag", "onItemClick: $position")
            }
            //设置页面选中监听
            onPageChanged = { position ->
                Log.e("tag", "onPageChanged: $position")
                //更新 tabLayout
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        }
    }

    /**
     * 填充 tabLayout items
     */
    private fun fillTabItems(tabLayout: TabLayout, itemCount: Int) {
        for (index in 0 until itemCount) {
            val newTab = tabLayout.newTab()
            val tabView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundResource(R.drawable.my_tab_indicator)
            }
            //设置 tab 使用自定义 view
            newTab.customView = tabView
            //设置 tab 不能点击
            newTab.view.isClickable = false
            tabLayout.addTab(newTab)
        }
    }

    /**
     * 业务 adapter
     */
    class RealAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val viewBinding = LayoutLoopViewPagerItemBinding.inflate(inflater, parent, false)
            return TestViewHolder(viewBinding)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder as TestViewHolder).viewBinding.testTv.text = "item: $position"
        }

        override fun getItemCount(): Int {
            return 3
        }

        class TestViewHolder(
            val viewBinding: LayoutLoopViewPagerItemBinding
        ) : RecyclerView.ViewHolder(viewBinding.root)

    }

    /**
     * 实现循环轮播的 adapter 包装类
     */
    class LoopAdapterWrapper<VH : RecyclerView.ViewHolder>(
        lifecycle: Lifecycle,
        private val viewPager2: ViewPager2,
        private val realAdapter: RecyclerView.Adapter<VH>,
        /**
         * 轮播间隔时长: 单位毫秒
         */
        private val loopInterval: Long = 3000,
        /**
         * 轮播方向: 正数表示往右边轮播, 否则表示往左边轮播
         */
        private val direction: Int = 1
    ) : RecyclerView.Adapter<VH>(), LifecycleEventObserver {

        /**
         * 实际的 item 个数
         */
        private var realItemCount: Int = 0

        /**
         * 轮播的最大 item 个数
         */
        private var maxItemCount: Int = 0

        /**
         * 自动轮播的任务
         */
        private val loopRunnable = object : Runnable {
            override fun run() {
                //计算下一个轮播的位置
                val nextLoopPosition = calculateNextLoopPosition()
                if (nextLoopPosition == -1) {
                    //没有下一个轮播位置了, 那就停止 loop
                    stopLoop()
                } else {
                    //准备继续轮播
                    viewPager2.setCurrentItem(nextLoopPosition, true)
                    viewPager2.postDelayed(this, loopInterval)
                }
            }
        }

        /**
         * 监听原始 adapter 的数据改变, 然后同步更新轮播 adapter
         */
        private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                onOriginAdapterChanged()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                onOriginAdapterChanged()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                onOriginAdapterChanged()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                onOriginAdapterChanged()
            }
        }

        /**
         * 页面选中回调, 回调的 position 为 realPosition
         */
        var onPageChanged: ((Int) -> Unit)? = null

        private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val realPosition = position % realItemCount
                onPageChanged?.invoke(realPosition)
            }
        }

        /**
         * 页面点击回调, 回调的 position 为 realPosition
         */
        var onItemClick: ((Int) -> Unit)? = null

        init {
            viewPager2.adapter = this
            prepareLoop()
            notifyDataSetChanged()
            lifecycle.addObserver(this)
            registerListeners()
        }

        private fun registerListeners() {
            realAdapter.registerAdapterDataObserver(adapterDataObserver)
            viewPager2.registerOnPageChangeCallback(onPageChangeCallback)
        }

        private fun unregisterListeners() {
            realAdapter.unregisterAdapterDataObserver(adapterDataObserver)
            viewPager2.unregisterOnPageChangeCallback(onPageChangeCallback)
        }

        private fun onOriginAdapterChanged() {
            stopLoop()
            prepareLoop()
            notifyDataSetChanged()
        }

        private fun prepareLoop() {
            realItemCount = realAdapter.itemCount
            maxItemCount = Int.MAX_VALUE - (Int.MAX_VALUE % realItemCount)
            val initPosition = (maxItemCount / 2) - ((maxItemCount / 2) % realItemCount)
            viewPager2.setCurrentItem(initPosition, false)
        }

        private fun calculateNextLoopPosition(): Int {
            //如果当前滑动到边界上了就不让再轮播了
            if (isCurrentLoopPositionInBoundary()) {
                return -1
            }
            val currentLoopPosition = getCurrentLoopPosition()
            return if (direction > 0) {
                //向右轮播
                currentLoopPosition + 1
            } else {
                //向左轮播
                currentLoopPosition - 1
            }
        }

        /**
         * 判断当前是否滑动到边界上了
         */
        private fun isCurrentLoopPositionInBoundary(): Boolean {
            val currentLoopPosition = getCurrentLoopPosition()
            return (direction > 0 && currentLoopPosition >= maxItemCount - 1)
                    || (direction <= 0 && currentLoopPosition <= 0)
        }

        /**
         * 获取当前轮播的 position
         * @see getCurrentRealPosition
         */
        fun getCurrentLoopPosition(): Int {
            return viewPager2.currentItem
        }

        /**
         * 获取当前真实的 position
         * @see getCurrentLoopPosition
         */
        fun getCurrentRealPosition(): Int {
            return getCurrentLoopPosition() % realItemCount
        }

        fun startLoop() {
            viewPager2.post {
                //如果当前滑动到边界上了就重置轮播状态
                if (isCurrentLoopPositionInBoundary()) {
                    prepareLoop()
                    notifyDataSetChanged()
                }
                viewPager2.removeCallbacks(loopRunnable)
                viewPager2.postDelayed(loopRunnable, loopInterval)
            }
        }

        fun stopLoop() {
            viewPager2.removeCallbacks(loopRunnable)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return realAdapter.onCreateViewHolder(parent, viewType).apply {
                itemView.setOnClickListener {
                    val realPosition = layoutPosition % 3
                    onItemClick?.invoke(realPosition)
                }
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val realPosition = position % realItemCount
            realAdapter.onBindViewHolder(holder, realPosition)
        }

        override fun getItemCount(): Int {
            return maxItemCount
        }

        /**
         * LifecycleObserver
         */
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    startLoop()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    stopLoop()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    unregisterListeners()
                    source.lifecycle.removeObserver(this)
                }
                else -> {
                    // no op
                }
            }
        }

    }

}