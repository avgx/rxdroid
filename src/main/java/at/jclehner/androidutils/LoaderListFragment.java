package at.jclehner.androidutils;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.actionbarsherlock.app.SherlockListFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class LoaderListFragment<T> extends SherlockListFragment implements LoaderManager.LoaderCallbacks<List<LoaderListFragment.LLFLoader.ItemHolder<T>>>
{
	public static abstract class LLFLoader<T> extends AsyncTaskLoader<List<? extends LLFLoader.ItemHolder<T>>>
	{
		public static class ItemHolder<T>
		{
			public ItemHolder(T item) {
				this.item = item;
			}

			public final T item;
		}

		static final List<?> EXCEPTION_IN_LOADER = Collections.unmodifiableList(new ArrayList<Object>(0));

		protected final Context mContext;
		private List<? extends ItemHolder<T>> mData;

		private volatile RuntimeException mException = null;

		public LLFLoader(Context context)
		{
			super(context);
			mContext = context;
		}

		@Override
		public void deliverResult(List<? extends ItemHolder<T>> data)
		{
			mData = data;

			if(isStarted())
				super.deliverResult(data);
		}

		@Override
		public final List<? extends ItemHolder<T>> onLoadInBackground()
		{
			try
			{
				mException = null;
				return loadInBackground();
			}
			catch(RuntimeException e)
			{
				mException = e;
				return (List<? extends ItemHolder<T>>) EXCEPTION_IN_LOADER;
			}
		}

		public RuntimeException getException() {
			return mException;
		}

		public abstract List<? extends ItemHolder<T>> loadInBackground();

		@Override
		protected void onStartLoading()
		{
			onContentChanged();

			if(mData != null)
				deliverResult(mData);

			if(takeContentChanged() || mData == null)
				forceLoad();
		}

		@Override
		protected void onStopLoading() {
			cancelLoad();
		}

		@Override
		protected void onReset()
		{
			super.onReset();
			onStopLoading();
			mData = null;
		}
	}

	public static abstract class LLFAdapter<T> extends BaseAdapter
	{
		protected final Context mContext;
		protected final LoaderListFragment<T> mFragment;
		protected final LayoutInflater mInflater;

		private List<? extends LLFLoader.ItemHolder<T>> mData;

		public LLFAdapter(LoaderListFragment<T> fragment)
		{
			mContext = fragment.getActivity();
			mFragment = fragment;
			mInflater = LayoutInflater.from(mContext);
		}

		public void setData(List<? extends LLFLoader.ItemHolder<T>> data)
		{
			mData = data;
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mData != null ? mData.size() : 0;
		}

		@Override
		public LLFLoader.ItemHolder<T> getItem(int position) {
			return mData.get(position);
		}

		@SuppressWarnings("unchecked")
		public <E extends LLFLoader.ItemHolder<T>> E getItemHolder(int position) {
			return (E) mData.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public abstract View getView(int position, View view, ViewGroup viewGroup);
	}

	private LLFAdapter<T> mAdapter;

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		mAdapter = onCreateAdapter();

		setListAdapter(mAdapter);
		setListShown(false);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public final Loader onCreateLoader(int i, Bundle bundle) {
		return onCreateLoader();
	}

	@Override
	public final void onLoadFinished(Loader<List<LLFLoader.ItemHolder<T>>> loader, List<LLFLoader.ItemHolder<T>> data)
	{
		if(data == LLFLoader.EXCEPTION_IN_LOADER)
			onLoaderException(((LLFLoader) loader).getException());

		mAdapter.setData(data);

		if(isResumed())
			setListShown(true);
		else
			setListShownNoAnimation(true);
	}

	@Override
	public final void onLoaderReset(android.support.v4.content.Loader loader) {
		mAdapter.setData(null);
	}

	protected abstract LLFAdapter<T> onCreateAdapter();
	protected abstract LLFLoader<T> onCreateLoader();

	protected void onLoaderException(RuntimeException e) {
		throw e;
	}
}
