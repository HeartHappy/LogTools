package com.hearthappy.log.preview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hearthappy.basic.ext.addListener
import com.hearthappy.log.LoggerX
import com.hearthappy.logs.databinding.ActivityPreviewBinding

class PreviewLogActivity : AppCompatActivity() {

    lateinit var viewBinding: ActivityPreviewBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.apply {
            val outPutters = LoggerX.getScopes()
            for (string in outPutters) {
                tabLayout.addTab(tabLayout.newTab().setText(string))
            }
            vp.adapter= object : FragmentStateAdapter(supportFragmentManager, lifecycle){
                override fun getItemCount(): Int {
                    return outPutters.size
                }

                override fun createFragment(position: Int): Fragment {
                    return PreviewLogFragment.newInstance(position)
                }
            }
            vp.addListener { tabLayout.getTabAt(it)?.select() }
            tabLayout.addListener(onSelect = {
                vp.currentItem = it.position
            })
            ivLogBack.setOnClickListener { finish() }
        }
    }
}