package com.vpe_soft.intime.intime;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity implements TaskFragment.OnFragmentInteractionListener {

    private boolean _itemIsSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        else if(id == R.id.action_newtask) {
            Log.d("VP","new task pressed");
            Intent intent = new Intent(this, NewTaskActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem acknowledge = menu.findItem(R.id.action_acknowledge_task);
        if(acknowledge != null) {
            acknowledge.setVisible(_itemIsSelected);
        }

        MenuItem delete = menu.findItem(R.id.action_delete_task);
        if(delete != null) {
            delete.setVisible(_itemIsSelected);
        }


        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onFragmentInteraction(String id) {
        _itemIsSelected = true;
        invalidateOptionsMenu();
    }

    public void OnEmptySpaceClick(View view) {
        _itemIsSelected = false;
        invalidateOptionsMenu();
    }
}
