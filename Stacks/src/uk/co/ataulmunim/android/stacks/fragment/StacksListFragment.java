package uk.co.ataulmunim.android.stacks.fragment;
import uk.co.ataulmunim.android.stacks.Crud;
import uk.co.ataulmunim.android.stacks.StacksCursorAdapter;
import uk.co.ataulmunim.android.stacks.contentprovider.Plans;
import uk.co.ataulmunim.android.stacks.contentprovider.Stacks;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockListFragment;
import com.nicedistractions.shortstacks.R;


public class StacksListFragment extends SherlockListFragment
	implements LoaderManager.LoaderCallbacks<Cursor>, OnEditorActionListener {
	
	public static final String LOG_TAG = "StacksListFragment";
	
	public static final String[] STACKS_PROJECTION = {
		Stacks._ID,	Stacks.NAME, Stacks.ACTION_ITEMS
	};
	
	public static final String[] PLANS_PROJECTION = {
		Plans._ID, Plans.DAY, Plans.STACK
	};
	
	public static final int STACKS_LOADER = 0;
	public static final int DATES_LOADER = 1;
	public static final int PLANS_LOADER = 2;
	
	/**
	 * Determines whether or not to close the soft input keyboard when adding Stacks to the list.
	 * A value of TRUE will leave it open, but this can be set in shared preferences.
	 */
	private boolean quickAddMode;
	
	private StacksCursorAdapter adapter;
	private int stackId; // id of the current stack in the Stacks table
		
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_stack_view, container, false);
    }
	
	
	
	/**
	 * Called after onCreateView(), after the parent activity is created
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		final Intent intent = getActivity().getIntent();
		final Uri stackUri = intent.getData();
		final String action = intent.getAction();
		stackId = intent.getIntExtra(Stacks._ID, Stacks.ROOT_STACK_ID);
		
		// Create an empty adapter we will use to display the loaded data.
		adapter = new StacksCursorAdapter(
					getActivity(),
					R.layout.list_item_stacks,
					null,
					new String[] {Stacks.NAME, Stacks.ACTION_ITEMS},
					new int[] { R.id.listitem_name, R.id.listitem_actionable_items }
					);
		
        setListAdapter(adapter);
        
        // Start out with a progress indicator.
        //setListShown(false);
        
        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getActivity().getSupportLoaderManager().initLoader(STACKS_LOADER, null, this);
        getActivity().getSupportLoaderManager().initLoader(PLANS_LOADER, null, this);
        
        // TODO: Get/set quickAddMode via SharedPreferences
        quickAddMode = true;
        ((EditText) getView().findViewById(R.id.add_stack_field)).setOnEditorActionListener(this);
	}
	
	/**
	 * Adds a stack as a child to the current stack.
	 */
	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		Log.d(LOG_TAG, "Done pressed.");
		// TODO: use the correct parent stack, not just the root stack
		Crud.addStack(getActivity(), v.getText().toString().trim(), null, Stacks.ROOT_STACK_ID);
		v.setText("");
		
		if (!quickAddMode) {
			Log.d(LOG_TAG, "quickAddMode false, hiding keyboard.");
			InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
					Activity.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
		}
	    return true;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		CursorLoader cursorLoader = null;
		
		if (id == STACKS_LOADER) {
			Log.d(LOG_TAG, "Loading Stacks for Stack " + stackId);
			final String where = Stacks.PARENT + "=" + stackId + " AND " + Stacks.DELETED + "<> 1";
			
			cursorLoader = new CursorLoader(getActivity(),
					Stacks.CONTENT_URI,
					STACKS_PROJECTION,
					where,
					null,
					Stacks.LOCAL_SORT);		
			
			return cursorLoader;	
		}
		
		else if (id == PLANS_LOADER ) {
			Log.d(LOG_TAG, "Loading Stacks Plans for Stack " + stackId);
			
			final String where = Plans.VALUE + "> 0";
			// URI for all plans which are connected to the Stacks table
			final Uri allPlans = Stacks.PLANS.getAll(Stacks.CONTENT_URI);
			
			cursorLoader = new CursorLoader(getActivity(), allPlans, PLANS_PROJECTION, where, null, Plans.STACK);
		}
		
		return cursorLoader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		if (loader.getId() == STACKS_LOADER) {
			Log.d(LOG_TAG, "Stacks loaded, swapping cursor, scrolling to end.");
			
			adapter.swapCursor(data);
			getListView().smoothScrollToPosition(adapter.getCount());
		}
		else if (loader.getId() == PLANS_LOADER) {
			Log.d(LOG_TAG, data.getCount() + " plans loaded");
			SparseArray<String> plans = new SparseArray<String>();
			
			/**
			 * The idea is to append. if empty add. if not empty append.
			 */
			if (data.moveToFirst()) {
				do {
					final int stack = data.getInt(data.getColumnIndex(Plans.STACK));
					final String plan = plans.get(stack);
					final int dayCode = data.getInt(data.getColumnIndex(Plans.DAY)); 
					final String day = Plans.getDayShort(dayCode);
					
					if (plan == null) {
						plans.put(stack, day);
					} else if (!plan.contains(day)){
						plans.put(stack, plan + " " + day); 
					}
					
					Log.d(LOG_TAG, "key: "+ stack+ ", plan: "+ plans.get(stack));
					
				} while (data.moveToNext());
			}
			
			// TODO: Update views already visible
			adapter.setCachedPlans(plans);
			adapter.notifyDataSetChanged();			
		}
	}
	
	

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if (loader.getId() == STACKS_LOADER) {
			Log.d(LOG_TAG, "Closing last Stacks cursor, so setting adapter cursor to null.");
			adapter.swapCursor(null);
		}
	}
	

	
}
