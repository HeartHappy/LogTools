package com.hearthappy.logtools.preview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hearthappy.basic.ext.addStateAdapter
import com.hearthappy.log.Logger
import com.hearthappy.logtools.databinding.ActivityPreviewBinding

class PreviewActivity: AppCompatActivity() {

    lateinit var viewBinding: ActivityPreviewBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.apply {
            val outputters = Logger.getScopes()
            vp.addStateAdapter(supportFragmentManager, lifecycle, outputters.size) {
                PreviewFragment.newInstance(it,outputters[it])
            }
        }
    }
}