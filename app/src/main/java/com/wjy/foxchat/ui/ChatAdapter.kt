package com.wjy.foxchat.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wjy.foxchat.databinding.ItemMessageAiBinding
import com.wjy.foxchat.databinding.ItemMessageUserBinding
import com.wjy.foxchat.model.Message
import java.io.File

class ChatAdapter(
    private val onMessageLongClick: (Message) -> Unit,
    private val onImageClick: (Message) -> Unit,
    private val onAudioClick: (Message) -> Unit
) : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Message>() {
        private const val VIEW_MINE = 1
        private const val VIEW_OTHER = 0

        override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id

        override fun areContentsTheSame(old: Message, new: Message) = old == new
        fun bindMessage(
            message: Message,
            quoted: Message?,
            content: android.widget.TextView,
            image: android.widget.ImageView,
            audio: android.widget.TextView,
            quotedView: android.widget.TextView,
            meta: android.widget.TextView,
            status: android.widget.TextView?,
            root: View,
            onLongClick: (Message) -> Unit,
            onImageClick: (Message) -> Unit,
            onAudioClick: (Message) -> Unit
        ) {
            val recalled = message.isRecalled
            val hasQuote = message.replyToMessageId != null
            quotedView.visibility = if (hasQuote) View.VISIBLE else View.GONE
            quotedView.text = when {
                quoted != null -> quotedText(quoted)
                hasQuote -> "引用：原消息不可用"
                else -> ""
            }
            content.visibility = if (!recalled && (message.isImage || message.isAudio)) {
                View.GONE
            } else {
                View.VISIBLE
            }
            image.visibility = if (!recalled && message.isImage) View.VISIBLE else View.GONE
            audio.visibility = if (!recalled && message.isAudio) View.VISIBLE else View.GONE
            content.text = if (recalled) "消息已撤回" else message.content
            image.setOnClickListener(null)
            audio.setOnClickListener(null)
            if (message.isImage) {
                image.setImageURI(message.mediaPath?.let(::pathToUri))
                image.setOnClickListener { onImageClick(message) }
            }
            if (message.isAudio) {
                audio.text = "语音 ${((message.mediaDurationMs / 1000).coerceAtLeast(1))}s"
                audio.setOnClickListener { onAudioClick(message) }
            }
            meta.text = android.text.format.DateFormat.format("HH:mm", message.timestamp)
            status?.text = if (message.isMine && !recalled) message.deliveryStatus else ""
            root.setOnLongClickListener {
                onLongClick(message)
                true
            }
        }

        private fun quotedText(message: Message): String {
            val preview = when {
                message.isRecalled -> "消息已撤回"
                message.isImage -> "图片消息"
                message.isAudio -> "语音消息"
                message.content.isNotBlank() -> message.content
                else -> "消息"
            }
            return "引用：$preview"
        }

        private fun pathToUri(path: String): Uri {
            val parsed = Uri.parse(path)
            return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(path)) else parsed
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) VIEW_MINE else VIEW_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_MINE) {
            MineViewHolder(
                ItemMessageUserBinding.inflate(inflater, parent, false),
                onMessageLongClick,
                onImageClick,
                onAudioClick
            )
        } else {
            OtherViewHolder(
                ItemMessageAiBinding.inflate(inflater, parent, false),
                onMessageLongClick,
                onImageClick,
                onAudioClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val quoted = message.replyToMessageId
            ?.let { replyId -> currentList.firstOrNull { it.id == replyId } }
        when (holder) {
            is MineViewHolder -> holder.bind(message, quoted)
            is OtherViewHolder -> holder.bind(message, quoted)
        }
    }

    override fun onCurrentListChanged(
        previousList: MutableList<Message>,
        currentList: MutableList<Message>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        val changedQuotedIds = currentList.mapNotNull { current ->
            val previous = previousList.firstOrNull { it.id == current.id }
            current.id.takeIf { previous != null && previous != current }
        }.toSet()
        currentList.forEachIndexed { index, message ->
            if (message.replyToMessageId in changedQuotedIds) notifyItemChanged(index)
        }
    }

    class MineViewHolder(
        private val binding: ItemMessageUserBinding,
        private val onLongClick: (Message) -> Unit,
        private val onImageClick: (Message) -> Unit,
        private val onAudioClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, quoted: Message?) = DiffCallback.bindMessage(
            message,
            quoted,
            binding.tvContent,
            binding.ivMedia,
            binding.tvAudio,
            binding.tvQuoted,
            binding.tvMeta,
            binding.tvStatus,
            binding.root,
            onLongClick,
            onImageClick,
            onAudioClick
        )
    }

    class OtherViewHolder(
        private val binding: ItemMessageAiBinding,
        private val onLongClick: (Message) -> Unit,
        private val onImageClick: (Message) -> Unit,
        private val onAudioClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, quoted: Message?) = DiffCallback.bindMessage(
            message,
            quoted,
            binding.tvContent,
            binding.ivMedia,
            binding.tvAudio,
            binding.tvQuoted,
            binding.tvMeta,
            null,
            binding.root,
            onLongClick,
            onImageClick,
            onAudioClick
        )
    }
}
