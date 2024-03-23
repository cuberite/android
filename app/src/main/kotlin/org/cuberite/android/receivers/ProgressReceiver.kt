package org.cuberite.android.receivers

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.cuberite.android.R

class ProgressReceiver(private val cont: Context, handler: Handler?) : ResultReceiver(handler) {
    private var progressDialog: AlertDialog? = null
    private lateinit var progressBar: LinearProgressIndicator

    private fun createDialog(title: String?) {
        val layout = View.inflate(cont, R.layout.dialog_progress, null)
        progressBar = layout.findViewById<View>(R.id.progressBar) as LinearProgressIndicator
        progressDialog = MaterialAlertDialogBuilder(cont)
                .setTitle(title)
                .setView(layout)
                .setCancelable(false)
                .create()
    }

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        super.onReceiveResult(resultCode, resultData)
        when (resultCode) {
            PROGRESS_START -> {
                val title = resultData!!.getString("title")
                if (progressDialog != null) {
                    progressDialog!!.setTitle(title)
                } else {
                    createDialog(title)
                }
                progressBar.isIndeterminate = true
                progressDialog!!.show()
            }

            PROGRESS_NEW_DATA -> {
                val progress = resultData!!.getInt("progress")
                val max = resultData.getInt("max")
                progressBar.isIndeterminate = false
                progressBar.setProgressCompat(progress, true)
                progressBar.setMax(max)
            }

            PROGRESS_END -> {
                progressDialog!!.dismiss()
                progressDialog = null
            }
        }
    }

    companion object {
        const val PROGRESS_START = 0
        const val PROGRESS_NEW_DATA = 1
        const val PROGRESS_END = 2
    }
}
