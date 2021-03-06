/**
 * RxDroid - A Medication Reminder
 * Copyright (C) 2011-2014 Joseph Lehner <joseph.c.lehner@gmail.com>
 *
 *
 * RxDroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RxDroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RxDroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package at.jclehner.rxdroid.ui;

import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import at.jclehner.rxdroid.BuildConfig;
import at.jclehner.rxdroid.DoseHistoryActivity;
import at.jclehner.rxdroid.DoseTime;
import at.jclehner.rxdroid.DoseView;
import at.jclehner.rxdroid.DrugListActivity;
import at.jclehner.rxdroid.R;
import at.jclehner.rxdroid.Settings;
import at.jclehner.rxdroid.Theme;
import at.jclehner.rxdroid.Version;
import at.jclehner.rxdroid.db.Drug;
import at.jclehner.rxdroid.db.Entries;
import at.jclehner.rxdroid.db.Schedule;
import at.jclehner.rxdroid.util.Constants;
import at.jclehner.rxdroid.util.DateTime;
import at.jclehner.rxdroid.util.Extras;
import at.jclehner.rxdroid.util.Timer;
import at.jclehner.rxdroid.util.Util;
import at.jclehner.rxdroid.widget.DrugNameView;
import at.jclehner.rxdroid.widget.DrugSupplyMonitor;

public class DrugOverviewAdapter extends AbsDrugAdapter
{
	private static final String TAG = DrugOverviewAdapter.class.getSimpleName();
	private static final boolean LOGV = BuildConfig.DEBUG;

	private static final int TYPE_WITH_SCHEDULE = 0;
	private static final int TYPE_WITHOUT_SCHEDULE = 1;

	private final Timer mTimer;
	private final boolean mDimDoseViews;
	private final boolean mShowingAll;
	private final int mNextDoseTime;

	public DrugOverviewAdapter(Activity activity, List<Drug> items, Date date, int activeDoseTime, int nextDoseTime, boolean showingAll)
	{
		super(activity, items, date, activeDoseTime);


		mTimer = LOGV ? new Timer() : null;
		mDimDoseViews = Settings.getBoolean(Settings.Keys.DIM_DOSE_VIEWS, true);
		mShowingAll = showingAll;
		mNextDoseTime = nextDoseTime;
	}

	@Override
	public View getView(int position, View v, ViewGroup parent)
	{
		if(LOGV && position == 0)
			mTimer.restart();

		final Drug drug = getItem(position);
		final boolean hasSchedule = getItemViewType(position) == TYPE_WITH_SCHEDULE;
		final DoseViewHolder holder;

		if(v == null)
		{
			v = mActivity.getLayoutInflater().inflate(
					hasSchedule ? R.layout.drug_view : R.layout.drug_view_no_schedule, null);

			holder = new DoseViewHolder();

			holder.name = (DrugNameView) v.findViewById(R.id.drug_name);
			holder.icon = (ImageView) v.findViewById(R.id.drug_icon);
			holder.missedDoseIndicator = (ImageView) v.findViewById(R.id.img_missed_dose_warning);
			holder.historyMenuFrame = (FrameLayout) v.findViewById(R.id.frame_history_menu);
			holder.historyMenu = v.findViewById(R.id.frame_history_menu);
			holder.currentSupply = (DrugSupplyMonitor) v.findViewById(R.id.text_supply);

			if(hasSchedule)
			{
				for(int i = 0; i != holder.doseViews.length; ++i)
				{
					final int doseViewId = Constants.DOSE_VIEW_IDS[i];
					holder.doseViews[i] = (DoseView) v.findViewById(doseViewId);
					mActivity.registerForContextMenu(holder.doseViews[i]);
				}

				holder.setDividersFromLayout(v);
			}

			v.setTag(holder);
		}
		else
			holder = (DoseViewHolder) v.getTag();

		//holder.name.setUnscrambledText(drug.getName());
		holder.name.setDrug(drug);
		holder.name.setTag(DrugListActivity.TAG_DRUG_ID, drug.getId());

		//holder.icon.setImageResource(drug.getIconResourceId());
		holder.icon.setImageResource(Util.getDrugIconDrawable(drug.getIcon()));
		holder.currentSupply.setDrugAndDate(drug, mAdapterDate);

		final Date today = DateTime.today();
		final boolean isToday = today.equals(mAdapterDate);
		final boolean isCurrentSupplyVisible;
		boolean isMissingDoseIndicatorVisible = false;

		if(isToday)
		{
			if(Entries.hasMissingDosesBeforeDate(drug, mAdapterDate))
				isMissingDoseIndicatorVisible = true;

			isCurrentSupplyVisible = drug.getRefillSize() != 0 || !drug.getCurrentSupply().isZero();
		}
		else
			isCurrentSupplyVisible = mAdapterDate.after(today);

		holder.missedDoseIndicator.setVisibility(isMissingDoseIndicatorVisible ? View.VISIBLE : View.GONE);
		holder.historyMenu.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v)
			{
				Intent intent = new Intent(mActivity, DoseHistoryActivity.class);
				intent.putExtra(Extras.DRUG_ID, drug.getId());
				mActivity.startActivity(intent);
			}
		});

		holder.currentSupply.setVisibility(isCurrentSupplyVisible ? View.VISIBLE : View.INVISIBLE);

		if(!hasSchedule && isToday && drug.isActive())
		{
			// Active drugs without a schedule are displayed for notification purposes. We hide
			// the menu items that are not relevant
			if(!mShowingAll)
			{
				holder.historyMenuFrame.setVisibility(isMissingDoseIndicatorVisible ? View.VISIBLE : View.GONE);
				holder.currentSupply.setVisibility(holder.currentSupply.hasLowSupplies() ? View.VISIBLE : View.GONE);
			}
			//el
		}

		if(hasSchedule)
		{
			int doseTime = Schedule.TIME_MORNING;

			final int maxDoseTimeForNoDim;

			if(mActiveDoseTime == Schedule.TIME_INVALID)
			{
				if(mNextDoseTime == Schedule.TIME_MORNING)
					maxDoseTimeForNoDim = Schedule.TIME_MORNING - 1;
				else
					maxDoseTimeForNoDim = DoseTime.before(mNextDoseTime);
			}
			else
				maxDoseTimeForNoDim = mActiveDoseTime;

			for(DoseView doseView : holder.doseViews)
			{
				if(!doseView.hasInfo(mAdapterDate, drug))
					doseView.setDoseFromDrugAndDate(mAdapterDate, drug);

				if(mDimDoseViews)
				{
					boolean dimmed = false;

					if(isToday)
					{
						if(doseTime <= maxDoseTimeForNoDim && !drug.getDose(doseTime, mAdapterDate).isZero())
							dimmed = Entries.countDoseEvents(drug, mAdapterDate, doseView.getDoseTime()) != 0;
						else
							dimmed = true;
					}

					doseView.setDimmed(dimmed);
				}

				++doseTime;
			}

			final int dividerVisibility;
			if(v.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
				dividerVisibility = View.GONE;
			else
				dividerVisibility = View.VISIBLE;

			for(int i = 0; i != holder.dividers.length; ++i)
			{
				final View divider = holder.dividers[i];
				if(divider != null)
					divider.setVisibility(dividerVisibility);
			}
		}

		if(LOGV && position == getCount() - 1)
		{
			final double elapsed = mTimer.elapsedSeconds();
			final int viewCount = getCount() * 4;
			final double timePerView = elapsed / viewCount;

			Log.v(TAG, mAdapterDate + ": " + viewCount + " views created in " + elapsed + "s (" + timePerView + "s per view)");
		}

		if(BuildConfig.DEBUG)
		{
			//holder.dividers[1].setVisibility(View.VISIBLE);
			//holder.dividers[2].setVisibility(View.VISIBLE);
		}

		return v;
	}

	@Override
	public int getItemViewType(int position)
	{
		final Drug drug = getItem(position);
		if(!drug.isActive() || !drug.hasDoseOnDate(mAdapterDate))
		{
			if(Entries.countDoseEvents(drug, mAdapterDate, null) == 0)
				return TYPE_WITHOUT_SCHEDULE;
		}

		return TYPE_WITH_SCHEDULE;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}
}
