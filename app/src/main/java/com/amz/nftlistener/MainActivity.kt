package com.amz.nftlistener

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amz.nftlistener.capture.NotificationAccess
import com.amz.nftlistener.data.UploadLogEntity
import com.amz.nftlistener.databinding.ActivityMainBinding
import com.amz.nftlistener.databinding.ItemUploadLogBinding
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val app = application as NftListenerApp
        binding.logList.layoutManager = LinearLayoutManager(this)
        binding.logList.adapter = adapter
        binding.urlInput.setText(app.settings.webhookUrl())
        binding.tokenInput.setText(app.settings.token())
        binding.permissionAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.saveButton.setOnClickListener {
            val error = app.settings.save(
                binding.urlInput.text?.toString().orEmpty(),
                binding.tokenInput.text?.toString().orEmpty(),
            )
            Toast.makeText(this, error ?: getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
        binding.testButton.setOnClickListener {
            lifecycleScope.launch { app.repository.enqueueTestEvent() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.repository.logs.collect { adapter.submit(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = NotificationAccess.isGranted(
            NotificationManagerCompat.getEnabledListenerPackages(this),
            packageName,
        )
        binding.permissionTitle.setText(if (granted) R.string.permission_on else R.string.permission_off)
        binding.permissionTitle.setTextColor(
            getColor(if (granted) R.color.status_on else R.color.status_off),
        )
        binding.permissionAction.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private class LogAdapter : RecyclerView.Adapter<LogAdapter.Holder>() {
        private val items = mutableListOf<UploadLogEntity>()
        private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

        fun submit(logs: List<UploadLogEntity>) {
            items.clear()
            items.addAll(logs)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
            val inflater = android.view.LayoutInflater.from(parent.context)
            return Holder(ItemUploadLogBinding.inflate(inflater, parent, false))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.logTitle.text = "${item.appLabel} · ${item.title}"
            val reason = if (item.reason.isBlank()) "" else " · ${item.reason}"
            holder.binding.logMeta.text =
                "${timeFormat.format(Date(item.loggedAt))} · ${item.status}$reason"
        }

        class Holder(val binding: ItemUploadLogBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
