package com.hearthappy.loggerx.preview

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.hearthappy.log.image.LogImageLoaderFactory
import com.hearthappy.loggerx.databinding.DialogImagePreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream


/**
 * Created Date: 2026/4/16
 * @author ChenRui
 * ClassDescription：预览大图
 */
class ImagePreviewDialogFragment : DialogFragment() {
    private var _binding: DialogImagePreviewBinding? = null
    private val binding get() = _binding!!
    private var currentFile: File? = null
    private var currentMime: String = "image/jpeg"
    private var scale = 1f
    private var currentRequestPath: String? = null

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
            val file = currentFile
            if (file == null) {
                Toast.makeText(requireContext(), "图片尚未加载完成", Toast.LENGTH_SHORT).show()
            } else {
                saveToGallery(file, currentMime)
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
            currentMime = previewData.mimeType.ifBlank { "image/jpeg" }
            loadOriginalFile(previewData)
        }
    }

    private suspend fun loadOriginalFile(previewData: ImagePreviewData) {
        val file = withContext(Dispatchers.IO) {
            val resolved = File(previewData.filePath)
            if (resolved.exists() && resolved.isFile && resolved.canRead() && resolved.length() > 0L) {
                resolved
            } else {
                null
            }
        }
        if (file == null) {
            binding.pbLoadingImage.isVisible = false
            Toast.makeText(requireContext(), "图片加载失败", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isSupportedImageFormat(file)) {
            binding.pbLoadingImage.isVisible = false
            Toast.makeText(requireContext(), "图片格式不兼容", Toast.LENGTH_SHORT).show()
            return
        }
        currentFile = file
        currentMime = detectMimeType(file)
        currentRequestPath = file.absolutePath
        LogImageLoaderFactory.get(requireContext()).loadOriginal(file.absolutePath) { bitmap ->
            if (!isAdded || _binding == null || currentRequestPath != file.absolutePath) {
                return@loadOriginal
            }
            binding.pbLoadingImage.isVisible = false
            if (bitmap == null) {
                Toast.makeText(requireContext(), "图片解码失败", Toast.LENGTH_SHORT).show()
            } else {
                binding.ivPreview.setImageBitmap(bitmap)
            }
        }
    }

    private fun isSupportedImageFormat(file: File): Boolean {
        return detectMimeType(file) in SUPPORTED_MIME_TYPES
    }

    private fun detectMimeType(file: File): String {
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(12)
                val length = input.read(header)
                if (length >= 12 &&
                    header[0] == 0x52.toByte() &&
                    header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() &&
                    header[3] == 0x46.toByte() &&
                    header[8] == 0x57.toByte() &&
                    header[9] == 0x45.toByte() &&
                    header[10] == 0x42.toByte() &&
                    header[11] == 0x50.toByte()
                ) {
                    return@use "image/webp"
                }
                if (length >= 8 &&
                    header[0] == 0x89.toByte() &&
                    header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() &&
                    header[3] == 0x47.toByte()
                ) {
                    return@use "image/png"
                }
                if (length >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                    return@use "image/jpeg"
                }
                "application/octet-stream"
            }
        }.getOrDefault("application/octet-stream")
    }

    private fun saveToGallery(file: File, mimeType: String) {
        val resolver = requireContext().contentResolver
        val suffix = when {
            mimeType.contains("webp") -> "webp"
            mimeType.contains("png") -> "png"
            mimeType.contains("gif") -> "gif"
            else -> "jpg"
        }
        val fileName = "loggerx_${System.currentTimeMillis()}.$suffix"
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
        output.use { out ->
            if (out != null) {
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
                out.flush()
                Toast.makeText(requireContext(), "已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentRequestPath = null
        binding.ivPreview.setImageDrawable(null)
        _binding = null
    }

    companion object {
        private const val ARG_OUTPUTTER_INDEX = "arg_outputter_index"
        private const val ARG_LOG_ID = "arg_log_id"
        private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

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
