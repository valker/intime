package com.vpe_soft.intime.intime.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.database.*
import com.vpe_soft.intime.intime.kotlin.contentView
import com.vpe_soft.intime.intime.kotlin.locale
import com.vpe_soft.intime.intime.kotlin.string
import com.vpe_soft.intime.intime.kotlin.toolbar
import com.vpe_soft.intime.intime.receiver.getNextAlarm
import kotlinx.android.synthetic.main.activity_new_task.*

class NewTaskActivity : AppCompatActivity() {
    private var operatedId = 0L
    private lateinit var operatedTask: Task
    private lateinit var activityAction: String
    private var editTextError = false
    private val tag = "NewTaskActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
        contentView = R.layout.activity_new_task
        spinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.spinnerItems)
        )
        newTaskAppbar.outlineProvider = null
        toolbar = newTaskToolbar
        description.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (editTextError) setEditTextState(0)
            }

            override fun afterTextChanged(s: Editable) {}
        })
        val extras = intent.extras
        val action =
            if (extras != null) extras.getString("action")!! else "create" // default action if nothing provided via extras
        activityAction = action
        when (action) {
            "create" -> {
                newTaskToolbar.title = getString(R.string.new_task_activity_title)
                with(numberPicker) {
                    maxValue = 10
                    minValue = 1
                    value = 1
                }
            }
            "edit" -> {
                newTaskToolbar.title = getString(R.string.edit_task_activity_title)
                val id = extras?.getLong("id")!!
                operatedId = id
                operatedTask = findTaskById(id)
                description.setText(operatedTask.description)
                spinner.setSelection(operatedTask.interval)
                with(numberPicker) {
                    maxValue = 10
                    minValue = 1
                    value = operatedTask.amount
                }
            }
            else -> finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu) = true
        .also { menuInflater.inflate(R.menu.menu_new_task, menu) }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_create -> {
                //TODO: wrap with extension
                if (activityAction == "create") {
                    if (description.text.isEmpty()) setEditTextState(1)
                    else createNewTask(formTask(System.currentTimeMillis()))
                        .also { finish() }
                } else {
                    if (description.text.isEmpty()) setEditTextState(1)
                    else update(formTask(operatedTask.lastAcknowledge))
                        .also { finish() }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setEditTextState(state: Int) =
        if (state == 0) {
            //normal state 757575
            description.setHintTextColor(editTextHint)
            DrawableCompat.setTint(description.background, editTextTint)
        } else {
            //error state
            editTextError = true
            description.setHintTextColor(editTextErrorHint)
            DrawableCompat.setTint(description.background, editTextErrorTint)
        }

    private fun formTask(lastAck: Long): Task {
        val amount = numberPicker.value
        val interval = spinner.selectedItemPosition
        val taskDescription = description.string
        val nextAlarm: Long =
            getNextAlarm(interval, amount, lastAck, locale)
        val cautionPeriod = ((nextAlarm - lastAck) * 0.95).toLong()
        val nextCaution = lastAck + cautionPeriod
        return Task(taskDescription, interval, amount, nextAlarm, nextCaution, lastAck)
    }

    private fun update(task: Task) {
        Log.d(tag, "updateTask")
        if (wasIntervalOrAmountChanged(task.interval, task.amount)) updateTask(operatedId, task)
        else updateTaskDescription(operatedId, task)
    }

    private fun wasIntervalOrAmountChanged(interval: Int, amount: Int) =
        operatedTask.interval != interval || operatedTask.amount != amount
}