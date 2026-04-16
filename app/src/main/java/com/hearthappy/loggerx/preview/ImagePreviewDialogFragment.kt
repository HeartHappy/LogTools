package com.hearthappy.loggerx.preview

import android.content.ContentValues
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
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.loggerx.databinding.DialogImagePreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

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
            val pair = withContext(Dispatchers.IO) {
                val scopeProxy: LogScopeProxy = LoggerX.getOutputters()[outputterIndex].scope.getProxy()
                val mime = scopeProxy.loadImageMimeType(logId).orEmpty().ifBlank { "image/jpeg" }
                val base64 = scopeProxy.loadImageBase64(logId)
                Pair(mime, base64)
            }
            currentMime = pair.first
            val bytes = runCatching { Base64.decode(pair.second, Base64.DEFAULT) }.getOrNull()
            binding.pbLoadingImage.isVisible = false
            if (bytes == null) {
                Toast.makeText(requireContext(), "图片数据读取失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            currentBytes = bytes
            binding.ivPreview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }
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
        _binding = null
    }

    companion object {
        private const val ARG_OUTPUTTER_INDEX = "arg_outputter_index"
        private const val ARG_LOG_ID = "arg_log_id"

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
