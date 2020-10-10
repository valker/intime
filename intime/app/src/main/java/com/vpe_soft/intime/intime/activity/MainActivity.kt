package com.vpe_soft.intime.intime.activity

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewOutlineProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vpe_soft.intime.intime.R
import com.vpe_soft.intime.intime.database.*
import com.vpe_soft.intime.intime.kotlin.cursor
import com.vpe_soft.intime.intime.kotlin.px
import com.vpe_soft.intime.intime.receiver.NOTIFICATION_TAG
import com.vpe_soft.intime.intime.receiver.TASK_OVERDUE_ACTION
import com.vpe_soft.intime.intime.receiver.setupAlarmIfRequired
import com.vpe_soft.intime.intime.recyclerview.TaskRecyclerViewAdapter
import com.vpe_soft.intime.intime.view.CardViewStateHelper
import com.vpe_soft.intime.intime.view.showDialog
import com.vpe_soft.intime.intime.view.showOnAcknowledged
import com.vpe_soft.intime.intime.view.showOnDeleted
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: TaskRecyclerViewAdapter
    private val stateHelper = CardViewStateHelper()
    private val receiver = MyBroadcastReceiver()
    private val tag = "MainActivity"
    var isDefaultViewOutlineProviderSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(tag, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mainAppbar.outlineProvider = null
        mainToolbar.title = getString(R.string.main_activity_title)
        setSupportActionBar(mainToolbar)
        //TODO: create empty view after deleting old empty view
        recyclerView.setBackgroundColor(cardSwipeBackground)
        recyclerView.layoutManager = LinearLayoutManager(this)
        with(TaskRecyclerViewAdapter(this)) {
            adapter = this
            recyclerView.adapter = adapter
        }
        val simpleItemTouchCallback: ItemTouchHelper.SimpleCallback = object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    stateHelper.setOnSwipeState(viewHolder as TaskRecyclerViewAdapter.TaskRecyclerViewVH)
                }
                super.onSelectedChanged(viewHolder, actionState)
            }

            override fun clearView(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ) {
                stateHelper.setDefaultState(viewHolder as TaskRecyclerViewAdapter.TaskRecyclerViewVH)
                super.clearView(recyclerView, viewHolder)
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val dx = dX.toInt()
                    val cardLeft = viewHolder.itemView.left
                    val cardRight = viewHolder.itemView.right
                    val cardTop = viewHolder.itemView.top
                    val cardBottom = viewHolder.itemView.bottom
                    var newCardLeft = 0
                    var newCardRight = 0
                    var imgLeft = 0
                    var imgRight = 0
                    val imgMargin = 24f.px
                    val img = ContextCompat.getDrawable(this@MainActivity, R.drawable.acknowledge)!!
                    val imgSize = 24f.px.toInt()
                    val imgTop = (cardTop + (cardBottom - cardTop) / 2 - imgSize / 2).toInt()
                    if (dx > 0) {
                        //right
                        newCardRight = cardLeft + dx
                        newCardLeft = cardLeft
                        imgLeft = (cardLeft + imgMargin).toInt()
                        imgRight = (cardLeft + imgMargin + imgSize).toInt()
                    } else if (dx < 0) {
                        //left
                        newCardLeft = cardRight + dx
                        newCardRight = cardRight
                        imgLeft = (cardRight - imgMargin - imgSize).toInt()
                        imgRight = (cardRight - imgMargin).toInt()
                    }
                    val background = ColorDrawable(cardSwipeBackground)
                    background.setBounds(newCardLeft, cardTop, newCardRight, cardBottom)
                    background.draw(canvas)
                    img.setBounds(imgLeft, imgTop, imgRight, imgTop + imgSize)
                    img.draw(canvas)
                }
                super.onChildDraw(
                    canvas,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                Log.d(tag, viewHolder.itemView.toString())
                val pos = viewHolder.adapterPosition
                acknowledgeTask(getId(pos), pos)
            }
        }
        ItemTouchHelper(simpleItemTouchCallback).attachToRecyclerView(recyclerView)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_task -> {
                val intent1 = Intent(this@MainActivity, NewTaskActivity::class.java)
                intent1.putExtra("action", "create")
                startActivity(intent1)
                true
            }
            R.id.action_settings -> {
                val intent2 = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(intent2)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        val filter = IntentFilter(TASK_OVERDUE_ACTION)
        registerReceiver(receiver, filter)
        super.onStart()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onPause() {
        Log.d(tag, "onPause")
        isOnScreen = false
        with(getSharedPreferences("SessionInfo", MODE_PRIVATE).edit()) {
            putLong("LastUsageTimestamp", System.currentTimeMillis())
            apply()
        }
        super.onPause()
    }

    override fun onResume() {
        Log.d(tag, "onResume")
        isOnScreen = true
        adapter.swapCursor(cursor)
        refreshRecyclerView()
        createAlarm()
        super.onResume()
    }

    private fun editTask(id: Long) {
        Log.d(tag, "editTask")
        Intent(this, NewTaskActivity::class.java)
            .putExtra("action", "edit")
            .putExtra("id", id)
            .also { startActivity(it) }
    }

    /**
     * Acknowledges the task with given ID and position with ability to roll-back unwanted ack
     *
     * @param id  - ID of the task
     * @param pos - position of the task in the list
     */
    private fun acknowledgeTask(id: Long, pos: Int) {
        Log.d(tag, "Id = $id")
        Log.d(tag, "Position = $pos")
        val currentTimeMillis = System.currentTimeMillis()
        val previousTaskState = databaseAcknowledge(id, currentTimeMillis) ?: return
        onTaskListUpdated()
        adapter.notifyItemChanged(pos)
        showOnAcknowledged(recyclerView) {
            rollbackState(id, previousTaskState)
            onTaskListUpdated()
        }
    }

    private fun onTaskListUpdated() {
        setupAlarmIfRequired()
        adapter.swapCursor(cursor)
    }

    private fun createAlarm() {
        Log.d(tag, "createAlarm")
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_TAG, 1)
        setupAlarmIfRequired()
    }

    fun refreshRecyclerView() {
        Log.d(tag, "refreshListView")
        adapter.notifyItemRangeChanged(0, databaseLength)
    }

    private fun deleteTask(id: Long): Task {
        Log.d(tag, "deleteTask")
        val task = findTaskById(id)
        deleteTask(id)
        return task
    }

    fun setDefaultViewOutlineProvider(viewOutlineProvider: ViewOutlineProvider?) {
        stateHelper.defaultProvider = viewOutlineProvider
    }

    fun onItemLongClicked(id: Long, pos: Int) {
        showDialog(acknowledge = {
            acknowledgeTask(id, pos)
        }, edit = {
            Log.d(tag, "id $id")
            Log.d(tag, "pos $pos")
            editTask(id)
        }, delete = {
            Log.d(tag, "delete: id=$id, pos=$pos")
            val task = deleteTask(id)
            onTaskListUpdated()
            with(adapter) {
                notifyItemRemoved(pos)
                notifyItemRangeChanged(pos, databaseLength)
            }
            showOnDeleted(recyclerView) {
                createNewTask(task, id)
                onTaskListUpdated()
                adapter.notifyItemInserted(pos)
            }
        })
    }

    private inner class MyBroadcastReceiver : BroadcastReceiver() {
        private val tag = "MyBroadcastReceiver"
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(tag, "onReceive")
            if (isOnScreen) refreshRecyclerView()
        }
    }

    companion object {
        var isOnScreen = true
    }
}