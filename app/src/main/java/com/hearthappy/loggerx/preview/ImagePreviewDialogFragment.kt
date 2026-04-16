package com.hearthappy.loggerx.preview

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.ImagePreviewData
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.loggerx.databinding.DialogImagePreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.OutputStream


/**
 * Created Date: 2026/4/16
 * @author ChenRui
 * ClassDescription：预览大图
 */
class ImagePreviewDialogFragment : DialogFragment() {
    private var _binding: DialogImagePreviewBinding? = null
    private val binding get() = _binding!!
    private var currentBytes: ByteArray? = null
    private var currentMime: String = "image/jpeg"
    private var scale = 1f

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogImagePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnZoomIn.setOnClickListener { updateScale(scale + 0.25f) }
        binding.btnZoomOut.setOnClickListener { updateScale(scale - 0.25f) }
        binding.btnRotate.setOnClickListener { binding.ivPreview.rotation = (binding.ivPreview.rotation + 90f) % 360f }
        binding.btnDownload.setOnClickListener {
            val bytes = currentBytes
            if (bytes == null) {
                Toast.makeText(requireContext(), "图片尚未加载完成", Toast.LENGTH_SHORT).show()
            } else {
                saveToGallery(bytes, currentMime)
            }
        }
        loadImageLazily()
    }

    private fun updateScale(newScale: Float) {
        scale = newScale.coerceIn(0.5f, 4f)
        binding.ivPreview.scaleX = scale
        binding.ivPreview.scaleY = scale
    }

    private fun loadImageLazily() {
        val outputterIndex = requireArguments().getInt(ARG_OUTPUTTER_INDEX)
        val logId = requireArguments().getInt(ARG_LOG_ID)
        binding.pbLoadingImage.isVisible = true
        lifecycleScope.launch {
            val previewData = withContext(Dispatchers.IO) {
                val scopeProxy: LogScopeProxy = LoggerX.getOutputters()[outputterIndex].scope.getProxy()
                scopeProxy.loadImagePreviewData(logId)
            }
            if (previewData == null) {
                binding.pbLoadingImage.isVisible = false
                Toast.makeText(requireContext(), "图片数据读取失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            currentMime = previewData.mimeType.ifBlank { "image/webp" }
            showThumbnail(previewData)
            val loadedCompressed = loadCompressedWithTimeout(previewData)
            binding.pbLoadingImage.isVisible = false
            if (!loadedCompressed && currentBytes == null) {
                Toast.makeText(requireContext(), "图片加载失败，已回退缩略图", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun showThumbnail(previewData: ImagePreviewData) {
        val thumbBytes = decodeBase64(previewData.thumbnailBase64)
        if (thumbBytes == null) return
        val thumbBitmap = withContext(Dispatchers.IO) { decodeBitmapSafely(thumbBytes, THUMB_MAX_SIDE) }
        if (thumbBitmap != null) {
            currentBytes = thumbBytes
            binding.ivPreview.setImageBitmap(thumbBitmap)
        }
    }

    private suspend fun loadCompressedWithTimeout(previewData: ImagePreviewData): Boolean {
        val compressedBytes = withContext(Dispatchers.IO) {
            withTimeoutOrNull(IMAGE_LOAD_TIMEOUT_MS) {
                decodeBase64(previewData.compressedBase64)
            }
        }
        if (compressedBytes == null) {
            Toast.makeText(requireContext(), "压缩图加载超时，展示缩略图", Toast.LENGTH_SHORT).show()
            return false
        }
        val bitmap = withContext(Dispatchers.IO) { decodeBitmapSafely(compressedBytes, PREVIEW_MAX_SIDE) }
        if (bitmap == null) {
            Toast.makeText(requireContext(), "压缩图解码失败，展示缩略图", Toast.LENGTH_SHORT).show()
            return false
        }
        currentBytes = compressedBytes
        binding.ivPreview.setImageBitmap(bitmap)
        return true
    }

    private fun decodeBase64(base64: String?): ByteArray? {
        if (base64.isNullOrBlank()) return null
        return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
    }

    private fun decodeBitmapSafely(bytes: ByteArray, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSample = 1
        var w = width
        var h = height
        while (w > maxSide || h > maxSide) {
            inSample *= 2
            w /= 2
            h /= 2
        }
        return inSample.coerceAtLeast(1)
    }

    private fun saveToGallery(bytes: ByteArray, mimeType: String) {
        val resolver = requireContext().contentResolver
        val fileName = "loggerx_${System.currentTimeMillis()}.${if (mimeType.contains("webp")) "webp" else "jpg"}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LoggerX")
            }
        }
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show()
            return
        }
        val output: OutputStream? = resolver.openOutputStream(uri)
        output.use {
            if (it != null) {
                it.write(bytes)
                it.flush()
                Toast.makeText(requireContext(), "已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.ivPreview.setImageDrawable(null)
        _binding = null
    }

    companion object {
        private const val ARG_OUTPUTTER_INDEX = "arg_outputter_index"
        private const val ARG_LOG_ID = "arg_log_id"
        private const val IMAGE_LOAD_TIMEOUT_MS = 3_000L
        private const val PREVIEW_MAX_SIDE = 2_048
        private const val THUMB_MAX_SIDE = 512

        fun newInstance(outputterIndex: Int, logId: Int): ImagePreviewDialogFragment {
            return ImagePreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_OUTPUTTER_INDEX, outputterIndex)
                    putInt(ARG_LOG_ID, logId)
                }
            }
        }
    }
}
