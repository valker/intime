package com.vpe_soft.intime.intime;

import android.app.Activity;
import android.util.Log;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.app.Fragment;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
public class TaskFragment extends Fragment implements AbsListView.OnItemClickListener {

    private static final String TAG = "TaskFragment";

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        tasksCursor.close();
    }

    private OnFragmentInteractionListener mListener;
    /**
     * The fragment's ListView/GridView.
     */
    private AbsListView mListView;

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private ListAdapter mAdapter;
    private Cursor tasksCursor;
    private static final String SKELETON = "HHmmss ddMMyyyy";

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TaskFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        InTimeOpenHelper openHelper = new InTimeOpenHelper(getActivity());
        SQLiteDatabase database = openHelper.getReadableDatabase();
        final String selection = null;
        final String[] selectionArgs = null;
        final String groupBy = null;
        final String having = null;
        tasksCursor = database.query(
                Util.TASK_TABLE,
                new String[]{
                        "description",
                        "id AS _id",
                        "next_alarm",
                        "next_caution"},
                selection, selectionArgs,
                groupBy,
                having,
                "next_alarm");
        mAdapter = new CursorAdapter(getActivity(), tasksCursor) {
            @Override
            public View newView(Context context, Cursor cursor, ViewGroup parent) {
                return LayoutInflater.from(context).inflate(R.layout.task_item, parent, false);
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                // Extract properties from cursor
                String description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                long next_alarm = cursor.getLong(cursor.getColumnIndexOrThrow("next_alarm"));
                long next_caution = cursor.getLong(cursor.getColumnIndexOrThrow("next_caution"));

                // get current system properties (locale & timestamp)
                final Locale locale = getResources().getConfiguration().locale;
                final long currentTimeMillis = System.currentTimeMillis();

                Date date = new Date(next_alarm);
                final String pattern = DateFormat.getBestDateTimePattern(locale, SKELETON);
                SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
                format.setTimeZone(TimeZone.getDefault());
                final String nextAlarm = format.format(date);
                // todo: change to '1' when yellowing algorithm will work OK
                final int type = currentTimeMillis > next_caution ? currentTimeMillis > next_alarm?2:0:0;
                populateItemViewFields(view, description, nextAlarm,type);
            }
        };
    }

    private static void populateItemViewFields(View view, String description, String nextAlarm,int type) {
        Log.d(TAG, "populateItemViewFields");
        // Find fields to populate in inflated template
        TextView tvBody = (TextView) view.findViewById(R.id.tvBody);
        TextView tvPriority = (TextView) view.findViewById(R.id.tvPriority);
        // Populate fields with extracted properties
        tvBody.setText(description);
        tvPriority.setText(nextAlarm);
		switch(type){
			case 0://white
				view.setBackground(new PaintDrawable(Color.WHITE));
			break;
			case 2://red
				view.setBackground(new PaintDrawable(Color.RED));
			break;
			case 1://yellow
				view.setBackground(new PaintDrawable(Color.YELLOW));
			break;
		}
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_task, container, false);

        // Set the adapter
        mListView = (AbsListView) view.findViewById(android.R.id.list);
        final View emptyView = view.findViewById(android.R.id.empty);
        mListView.setEmptyView(emptyView);
        mListView.setAdapter(mAdapter);

        // Set OnItemClickListener so we can be notified on item clicks
        mListView.setOnItemClickListener(this);

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (null != mListener) {
            // Notify the active callbacks interface (the activity, if the
            // fragment is attached to one) that an item has been selected.
            mListener.onFragmentInteraction("" + id);
        }
    }

    public void refreshListView(){
        Log.d(TAG, "refreshListView");
        tasksCursor.requery();
        mListView.refreshDrawableState();
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(String id);
    }
}
