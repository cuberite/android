package org.cuberite.android.fragments

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import org.cuberite.android.MainActivity
import org.cuberite.android.R
import org.cuberite.android.services.CuberiteService

class ConsoleFragment : Fragment() {
    private lateinit var logView: TextView
    private lateinit var inputLine: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_console, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        CuberiteService.updateLogLiveData.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                return@observe
            }
            updateLog(result)
        }
        logView = view.findViewById(R.id.logView)
        inputLine = view.findViewById(R.id.inputLine)
        inputLine.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val command = inputLine.getText().toString()
                sendExecuteCommand(command)
                inputLine.setText("")
                // return true makes sure the keyboard doesn't close
                return@setOnEditorActionListener true
            }
            false
        }
        val textInputLayout = view.findViewById<TextInputLayout>(R.id.inputWrapper)
        textInputLayout.setEndIconOnClickListener {
            val command = inputLine.getText().toString()
            sendExecuteCommand(command)
            inputLine.setText("")
        }
    }

    private fun sendExecuteCommand(command: String) {
        if (command.isNotEmpty() && CuberiteService.isRunning) {
            // Logging tag
            val log = "Cuberite/Console"
            Log.d(log, "Executing $command")
            MainActivity.executeCommandLiveData.postValue(command)
        }
    }

    private fun updateLog(output: StringBuilder) {
        val scrollView = logView.parent as ScrollView
        val shouldScroll = logView.bottom - (scrollView.height + scrollView.scrollY) <= 0
        val formattedOutput = SpannableStringBuilder()
        for (line in output.toString().split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (line.isEmpty()) {
                continue
            }
            if (formattedOutput.isNotEmpty()) {
                // Line break
                formattedOutput.append("\n")
            }
            var color = -1
            var processedLine = line
            if (line.lowercase().startsWith("log: ")) {
                processedLine = line.replaceFirst("(?i)log: ".toRegex(), "")
            } else if (line.lowercase().startsWith("info: ")) {
                processedLine = line.replaceFirst("(?i)info: ".toRegex(), "")
                color = com.google.android.material.R.attr.colorTertiary
            } else if (line.lowercase().startsWith("warning: ")) {
                processedLine = line.replaceFirst("(?i)warning: ".toRegex(), "")
                color = com.google.android.material.R.attr.colorError
            } else if (line.lowercase().startsWith("error: ")) {
                processedLine = line.replaceFirst("(?i)error: ".toRegex(), "")
                color = com.google.android.material.R.attr.colorOnErrorContainer
            }
            val logLine = SpannableStringBuilder(processedLine)
            if (color >= 0) {
                val start = 0
                val end = logLine.length
                color = MaterialColors.getColor(requireContext(), color, Color.BLACK)
                logLine.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            formattedOutput.append(logLine)
        }
        logView.text = formattedOutput
        if (shouldScroll) {
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                inputLine.requestFocus()
            }
        }
    }
}
