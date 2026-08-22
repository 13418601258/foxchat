package com.wjy.foxchat.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wjy.foxchat.databinding.ActivityMessageActionsBinding

class MessageActionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMessageActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        findViewById<android.widget.TextView>(com.wjy.foxchat.R.id.tvTitle).text = "消息操作"
        binding.tvMessage.text = intent.getStringExtra(EXTRA_PREVIEW).orEmpty()
        findViewById<android.widget.ImageButton>(com.wjy.foxchat.R.id.btnBack).setOnClickListener { finish() }
        binding.btnReply.setOnClickListener { select(ACTION_REPLY) }
        binding.btnCopy.setOnClickListener { select(ACTION_COPY) }
        binding.btnRecall.visibility = if (intent.getBooleanExtra(EXTRA_CAN_RECALL, false)) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        binding.btnRecall.setOnClickListener { select(ACTION_RECALL) }
        binding.btnDelete.setOnClickListener { select(ACTION_DELETE) }
    }

    private fun select(action: String) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_ACTION, action)
        )
        finish()
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PREVIEW = "message_preview"
        const val EXTRA_CAN_RECALL = "can_recall"
        const val EXTRA_ACTION = "message_action"
        const val ACTION_REPLY = "reply"
        const val ACTION_COPY = "copy"
        const val ACTION_RECALL = "recall"
        const val ACTION_DELETE = "delete"

        fun newIntent(context: Context, id: String, preview: String, canRecall: Boolean) =
            Intent(context, MessageActionsActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, id)
                putExtra(EXTRA_PREVIEW, preview)
                putExtra(EXTRA_CAN_RECALL, canRecall)
            }
    }
}
