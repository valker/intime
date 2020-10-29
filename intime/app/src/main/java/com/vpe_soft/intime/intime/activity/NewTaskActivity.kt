package com.vpe_soft.intime.intime.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.database.*
import com.vpe_soft.intime.intime.kotlin.*
import com.vpe_soft.intime.intime.receiver.getNextAlarm
import kotlinx.android.synthetic.main.activity_new_task.*

class NewTaskActivity : AppCompatActivity(), Taggable {
    private var operatedId = 0L
    private lateinit var operatedTask: Task
    private lateinit var activityAction: String
    private var editTextError = false
    override val tag = "NewTaskActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("onCreate")
        contentView = R.layout.activity_new_task
        spinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            resources.getStringArray(R.array.spinnerItems)
        )
        newTaskAppbar.outlineProvider = null
        toolbar = newTaskToolbar
        description.textChangesListener = { if (editTextError) setEditTextState(0) }
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
                description.value = operatedTask.description
                spinner.selection = operatedTask.interval
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
                if (activityAction == "create") leave {
                    createNewTask(millis.formTask)
                }
                else leave {
                    update(operatedTask.lastAcknowledge.formTask)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun leave(work: () -> Unit) {
        if (description.text.isEmpty()) setEditTextState(1)
        else work().also { finish() }
    }

    private fun setEditTextState(state: Int) =
        if (state == 0) { //normal state
            editTextError = false
            description.apply {
                hintColor = editTextHint
                tint = editTextTint
            }
        } else { //error state
            editTextError = true
            description.apply {
                hintColor = editTextErrorHint
                tint = editTextErrorTint
            }
        }

    private val Long.formTask: Task
        get() {
            val amount = numberPicker.value
            val interval = spinner.selectedItemPosition
            val taskDescription = description.value
            val nextAlarm: Long = getNextAlarm(interval, amount, this, locale)
            val cautionPeriod = ((nextAlarm - this) * 0.95).toLong()
            val nextCaution = this + cautionPeriod
            return Task(taskDescription, interval, amount, nextAlarm, nextCaution, this)
        }

    private fun update(task: Task) {
        log("updateTask")
        if (wasIntervalOrAmountChanged(task.interval, task.amount)) updateTask(operatedId, task)
        else updateTaskDescription(operatedId, task)
    }

    private fun wasIntervalOrAmountChanged(interval: Int, amount: Int) =
        operatedTask.interval != interval || operatedTask.amount != amount
}
