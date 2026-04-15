package com.hearthappy.loggerx.preview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hearthappy.basic.ext.addListener
import com.hearthappy.basic.ext.addStateAdapter
import com.hearthappy.log.LoggerX
import com.hearthappy.loggerx.databinding.ActivityPreviewBinding

class PreviewActivity: AppCompatActivity() {

    lateinit var viewBinding: ActivityPreviewBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.apply {
            val outputters = LoggerX.getScopes()
            for (string in outputters) {
                tabLayout.addTab(tabLayout.newTab().setText(string))
            }

            vp.addStateAdapter(supportFragmentManager, lifecycle, outputters.size) {
                PreviewFragment.newInstance(it, outputters[it])
            }
            vp.addListener { tabLayout.getTabAt(it)?.select() }
            tabLayout.addListener(onSelect = {
                vp.currentItem = it.position
            })
        }
    }
}