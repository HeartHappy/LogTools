package com.hearthappy.loggerx

import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.hearthappy.basic.ext.dp2px
import com.hearthappy.log.image.ILogImageLoader

class LogImageLoaderImpl: ILogImageLoader {

    override fun loadThumbnail(imageView: ImageView, path: String) {
        Log.d("TAG", "loadThumbnail: $path")
        // 获取ImageView的尺寸用于缩略图加载
        Glide.with(imageView.context)
            .load(path)
            .transform(RoundedCorners(24.dp2px())) // 8px corner radius
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(imageView)
    }

    override fun loadOriginal(imageView: ImageView, path: String) {
        // 为原图设置最大尺寸限制，避免内存溢出
        Glide.with(imageView.context)
            .load(path)
            .transform(RoundedCorners(24.dp2px())) // 8px corner radius
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(imageView)
    }

}