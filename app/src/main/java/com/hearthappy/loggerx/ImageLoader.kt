package com.hearthappy.loggerx

import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.log.image.ILogImageLoader

class LogImageLoaderImpl: ILogImageLoader {

    override fun loadThumbnail(imageView: ImageView, path: String) {
        val requestOptions = RequestOptions()
            .override(48.dp2px(),48.dp2px())
            .transform(CenterCrop(),RoundedCorners(8.dp2px())) // 应用圆角变换
        Glide.with(imageView.context)
            .load(path)
            .apply(requestOptions)
            .into(imageView)
    }

    override fun loadOriginal(imageView: ImageView, path: String) {
        // 为原图设置最大尺寸限制，避免内存溢出
        val requestOptions = RequestOptions()
            .transform(FitCenter(),RoundedCorners(8.dp2px())) // 应用圆角变换
        Glide.with(imageView.context)
            .load(path)
            .apply(requestOptions)
            .into(imageView)
    }

}