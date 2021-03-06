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

package at.jclehner.rxdroid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import at.jclehner.androidutils.PreferenceActivity;
import at.jclehner.rxdroid.Settings.Keys;
import at.jclehner.rxdroid.db.Database;
import at.jclehner.rxdroid.db.DatabaseHelper;
import at.jclehner.rxdroid.db.DoseEvent;
import at.jclehner.rxdroid.db.Drug;
import at.jclehner.rxdroid.db.Schedule;
//import at.jclehner.rxdroid.ui.LayoutTestActivity;
import at.jclehner.rxdroid.db.SchedulePart;
import at.jclehner.rxdroid.util.CollectionUtils;
import at.jclehner.rxdroid.util.DateTime;
import at.jclehner.rxdroid.util.Util;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import net.lingala.zip4j.exception.ZipException;

@SuppressWarnings("deprecation")
public class PreferencesActivity extends PreferenceActivityBase implements
		OnSharedPreferenceChangeListener, OnPreferenceClickListener, OnPreferenceChangeListener
{
	private static final String TAG = PreferencesActivity.class.getSimpleName();

	private static final String[] KEEP_DISABLED = {
		Settings.Keys.VERSION, Settings.Keys.DB_STATS
	};

	private static final String[] REGISTER_CLICK_LISTENER = {
		Keys.LICENSES,
		Keys.VERSION
	};

	private static final String[] REGISTER_CHANGE_LISTENER = {
		Keys.THEME_IS_DARK,
		Keys.NOTIFICATION_LIGHT_COLOR,
		Keys.LOW_SUPPLY_THRESHOLD,
		Keys.LANGUAGE
	};

	private static final int MENU_RESTORE_DEFAULTS = 0;

	@TargetApi(11)
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		enqueuePreferencesFromResource(R.xml.preferences);
		super.onCreate(savedInstanceState);

		//addPreferencesFromResource(R.xml.preferences);

		Settings.registerOnChangeListener(this);

		for(Preference p : getPreferences(REGISTER_CHANGE_LISTENER))
			p.setOnPreferenceChangeListener(this);

		for(Preference p : getPreferences(REGISTER_CLICK_LISTENER))
			p.setOnPreferenceClickListener(this);

		Preference p = findPreference(Settings.Keys.VERSION);
		if(p != null)
		{
			final int format = BuildConfig.DEBUG ? Version.FORMAT_FULL : Version.FORMAT_SHORT;
			final StringBuilder sb = new StringBuilder(Version.get(format));

			if(BuildConfig.DEBUG)
				sb.append(" (DEV)");

			sb.append(", DB v" + DatabaseHelper.DB_VERSION);

			if(BuildConfig.DEBUG)
			{
				try
				{
					final String apkModDate = new Date(new File(getPackageCodePath()).lastModified()).toString();
					sb.append("\n(" + apkModDate + ")");
				}
				catch(NullPointerException e)
				{
					// eat
				}
			}

			sb.append("\n" +
					"Copyright (C) 2011-2014 Joseph Lehner\n" +
					"<joseph.c.lehner@gmail.com>");

			final String translator = getString(R.string.translator);
			if(!translator.equals("builtin"))
			{
				final Locale l = Locale.getDefault();

				if(Version.SDK_IS_HONEYCOMB_OR_NEWER)
					sb.append("\n\n");
				else
				{
					// Preference appears to be limited in height on pre-HC
					// devices... Prefix with an en-dash to make it look a
					// little less ugly!
					sb.append("\n\u2013 ");
				}

				sb.append(Util.capitalize(l.getDisplayLanguage(l))  + ": " + translator);
			}

			p.setSummary(sb.toString());
		}

		p = findPreference(Settings.Keys.HISTORY_SIZE);
		if(p != null)
			Util.populateListPreferenceEntryValues(p);

		p = findPreference(Settings.Keys.DONATE);
		if(p != null)
		{
			final String uriString;
			final int titleResId;
			final String summary;

			if(/*BuildConfig.DEBUG ||*/ Util.wasInstalledViaGooglePlay())
			{
				// Google Play doesn't allow donations using PayPal,
				// so we show a link to the project's website instead.
				uriString = "http://code.google.com/p/rxdroid";
				titleResId = R.string._title_website;
				summary = uriString;
			}
			else
			{
				uriString = "https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&business=joseph%2ec%2elehner%40gmail%2ecom&lc=AT&item_name=RxDroid&amount=5%2e00&currency_code=EUR&button_subtype=services&bn=PP%2dBuyNowBF%3abtn_buynowCC_LG%2egif%3aNonHosted";
				titleResId = R.string._title_donate;
				summary = null;
			}

			final Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setData(Uri.parse(uriString));

			p.setIntent(intent);
			p.setEnabled(true);
			p.setTitle(titleResId);
			p.setSummary(summary);
		}

		p = findPreference(Settings.Keys.DB_STATS);
		if(p != null)
		{
			final long millis = Database.getLoadingTimeMillis();
			final String str = new Formatter((Locale) null).format("%1.3fs", millis / 1000f).toString();
			p.setSummary(getString(R.string._msg_db_stats, str));
		}

		removeDisabledPreferences(getPreferenceScreen());
		setPreferenceListeners();

		if(Settings.getBoolean(Keys.USE_SAFE_MODE, false))
		{
			p = findPreference(Keys.SKIP_DOSE_DIALOG);
			if(p != null)
				p.setEnabled(false);
		}

		if(!BuildConfig.DEBUG)
		{
			p = findPreference("prefscreen_development");
			if(p != null)
				getPreferenceScreen().removePreference(p);
		}
		else
			setupDebugPreferences();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		updateLowSupplyThresholdPreferenceSummary();
	}

	@TargetApi(11)
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if(BuildConfig.DEBUG && false)
		{
			MenuItem item = menu.add(0, MENU_RESTORE_DEFAULTS, 0, R.string._title_factory_reset)
					.setIcon(R.drawable.ic_action_undo);

			item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		}

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case MENU_RESTORE_DEFAULTS:
				showDialog(R.id.preference_reset_dialog);
				return true;

			default:
				// ignore
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if(Settings.Keys.LOW_SUPPLY_THRESHOLD.equals(key))
			updateLowSupplyThresholdPreferenceSummary();
		else if(Settings.Keys.HISTORY_SIZE.equals(key))
		{
			if(Settings.getStringAsInt(Settings.Keys.HISTORY_SIZE, -1) >= Settings.Enums.HISTORY_SIZE_6M)
				Toast.makeText(getApplicationContext(), R.string._toast_large_history_size, Toast.LENGTH_LONG).show();
		}
		else if(Keys.USE_SAFE_MODE.equals(key))
		{
			final boolean useSafeMode = sharedPreferences.getBoolean(key, false);
			findPreference(Keys.SKIP_DOSE_DIALOG).setEnabled(!useSafeMode);
			if(useSafeMode)
				sharedPreferences.edit().putBoolean(Keys.SKIP_DOSE_DIALOG, false).commit();

			NotificationReceiver.cancelNotifications();
		}
		else if(Settings.Keys.LAST_MSG_HASH.equals(key))
			return;

		NotificationReceiver.rescheduleAlarmsAndUpdateNotification(true);
	}

	@Override
	public boolean onPreferenceClick(Preference preference)
	{
		final String key = preference.getKey();

		if(Settings.Keys.LICENSES.equals(key))
		{
			showDialog(R.id.licenses_dialog);
			return true;
		}
		else if(Settings.Keys.VERSION.equals(key))
		{
			final Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("plain/text");
			intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "josephclehner+rxdroid-feedback@gmail.com" });
			intent.putExtra(Intent.EXTRA_SUBJECT, "RxDroid");

			try
			{
				startActivity(intent);
			}
			catch(ActivityNotFoundException e)
			{
				// Happens if no mail client is installed
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue)
	{
		final String key = preference.getKey();

		if(Settings.Keys.THEME_IS_DARK.equals(key))
		{
			Theme.clearAttributeCache();

			final Context context = RxDroid.getContext();

			RxDroid.toastLong(R.string._toast_theme_changed);

			final PackageManager pm = context.getPackageManager();
			final Intent intent = pm.getLaunchIntentForPackage(context.getPackageName());
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			RxDroid.doStartActivity(intent);

			finish();
		}
		else if(Settings.Keys.NOTIFICATION_LIGHT_COLOR.equals(key))
		{
			final String value = (String) newValue;
			if(!("".equals(value) || "0".equals(value)))
			{
				if(!Settings.wasDisplayedOnce("custom_led_color"))
				{
					RxDroid.toastLong(R.string._toast_custom_led_color);
					Settings.setDisplayedOnce("custom_led_color");
				}
			}
		}
		else if(Settings.Keys.LANGUAGE.equals(key))
		{
			finish();
			//new Handler().po
		}
		else if(Keys.LOW_SUPPLY_THRESHOLD.equals(key))
		{
			int i;

			try
			{
				i = Integer.parseInt((String) newValue, 10);
			}
			catch(Exception e)
			{
				Log.w(TAG, e);
				return false;
			}

			return i >= 0;
		}

		return true;
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		if(id == R.id.licenses_dialog)
		{
			String license;
			InputStream is = null;

			try
			{
				final AssetManager aMgr = getResources().getAssets();
				is = aMgr.open("licenses.html", AssetManager.ACCESS_BUFFER);

				license = Util.streamToString(is);
			}
			catch(IOException e)
			{
				Log.w(TAG, e);
				license = "Licensed under the GNU GPLv3";
			}
			finally
			{
				Util.closeQuietly(is);
			}

			final WebView wv = new WebView(this);
			wv.loadDataWithBaseURL("file", license, "text/html", null, null);

			final AlertDialog.Builder ab = new AlertDialog.Builder(this);
			ab.setTitle(R.string._title_licenses);
			ab.setView(wv);
			ab.setPositiveButton(android.R.string.ok, null);

			return ab.create();
		}
		else if(id == R.id.preference_reset_dialog)
		{
			final AlertDialog.Builder ab= new AlertDialog.Builder(this);
			ab.setIcon(android.R.drawable.ic_dialog_alert);
			ab.setTitle(R.string._title_restore_default_settings);
			ab.setNegativeButton(android.R.string.cancel, null);
			/////////////////////
			ab.setPositiveButton(android.R.string.ok, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					Settings.clear();
				}
			});

			return ab.create();
		}

		return super.onCreateDialog(id);
	}

	@Override
	protected Intent getHomeButtonIntent()
	{
		Intent intent = new Intent(getBaseContext(), DrugListActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		return intent;
	}

	@Override
	protected boolean shouldHideOptionsMenuInSubscreens() {
		return true;
	}

	private void updateLowSupplyThresholdPreferenceSummary()
	{
		Preference p = findPreference(Settings.Keys.LOW_SUPPLY_THRESHOLD);
		if(p != null)
		{
			String value = Settings.getString(Settings.Keys.LOW_SUPPLY_THRESHOLD, "10");
			p.setSummary(getString(R.string._summary_min_supply_days, value));
		}
	}

	private void removeDisabledPreferences(PreferenceGroup root)
	{
		final List<Preference> toRemove = new ArrayList<Preference>();

		for(int i = 0; i != root.getPreferenceCount(); ++i)
		{
			final Preference p = root.getPreference(i);

			if(CollectionUtils.contains(KEEP_DISABLED, p.getKey()))
				continue;

			if(p instanceof PreferenceGroup)
				removeDisabledPreferences((PreferenceGroup) p);
			else if(!p.isEnabled())
				toRemove.add(p);
		}

		for(Preference p : toRemove)
			root.removePreference(p);
	}

	private void setPreferenceListeners()
	{
		final PreferenceScreen ps = getPreferenceScreen();

		for(int i = 0; i != ps.getPreferenceCount(); ++i)
		{
			final Preference p = ps.getPreference(i);
			p.setOnPreferenceChangeListener(this);
			//p.setOnPreferenceClickListener(this);
		}
	}

	private List<Preference> getPreferences(String[] keys)
	{
		final ArrayList<Preference> list = new ArrayList<Preference>(keys.length);
		for(String key : keys)
		{
			final Preference p = findPreference(key);
			if(p != null)
				list.add(p);
		}

		return list;
	}

	private void setupDebugPreferences()
	{
		Preference p = findPreference("db_create_drug_with_schedule");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					final int drugCount = Database.countAll(Drug.class);

					Fraction dose = new Fraction(1, 2);

					Schedule schedule = new Schedule();
					schedule.setDose(Schedule.TIME_MORNING, dose);
					schedule.setDose(Schedule.TIME_EVENING, dose);

					Date today = DateTime.today();

					schedule.setBegin(today);
					schedule.setEnd(DateTime.add(today, Calendar.DAY_OF_MONTH, 14));

					// first four days of the week
					SchedulePart part1 = new SchedulePart(0x78, new Fraction[]
							{ Fraction.ZERO, Fraction.ZERO, new Fraction(1, 2), Fraction.ZERO});

					// remaining three days of the week
					SchedulePart part2 = new SchedulePart(0x7, new Fraction[]
							{ Fraction.ZERO, Fraction.ZERO, new Fraction(1, 4), Fraction.ZERO});

					schedule.setScheduleParts(new SchedulePart[] { part1, part2 });

					Drug drug = new Drug();
					drug.setName("Drug #" + (drugCount + 1));
					drug.addSchedule(schedule);
					drug.setRepeatMode(Drug.REPEAT_CUSTOM);
					drug.setActive(true);

					Database.create(drug);
					Database.create(schedule);
					Database.create(part1);
					Database.create(part2);

					return true;
				}
			});
		}

		p = findPreference("db_create_drug_with_many_dose_events");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					Fraction dose = new Fraction(1, 2);

					Drug drug = new Drug();
					drug.setName("Megabite");
					drug.setDose(Schedule.TIME_MORNING, dose);
					drug.setRefillSize(30);
					drug.setCurrentSupply(new Fraction(23, 1, 2));

					Database.create(drug);

					Date date;

					for(int i = 0; i != 100; ++i)
					{
						date = DateTime.add(DateTime.today(), Calendar.DAY_OF_MONTH, -i);
						Database.create(new DoseEvent(drug, date, Schedule.TIME_MORNING, dose), Database.FLAG_DONT_NOTIFY_LISTENERS);
					}



					return true;
				}
			});
		}

		p = findPreference("key_debug_add_5_drugs");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					for(int i = 0; i != 5; ++i)
					{
						Drug drug = new Drug();
						drug.setName("Drug #" + Database.countAll(Drug.class));
						drug.setDose(Schedule.TIME_MORNING, new Fraction(1, 2));
						drug.setDose(Schedule.TIME_EVENING, new Fraction(1));
						drug.setRepeatMode(Drug.REPEAT_DAILY);
						drug.setActive(true);

						try
						{
							Database.create(drug);
						}
						catch(Exception e)
						{
							Log.w(TAG, e);
						}
					}

					return true;
				}
			});
		}

		p = findPreference("key_debug_crash_app");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					RxDroid.runInMainThread(new Runnable() {

						@Override
						public void run()
						{
							throw new RuntimeException("Crash requested by user");
						}
					});
					return true;
				}
			});
		}

		p = findPreference("key_debug_tablet_layout");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

				@Override
				public boolean onPreferenceClick(Preference preference)
				{
//					Intent intent = new Intent(getApplicationContext(), LayoutTestActivity.class);
//					intent.putExtra(LayoutTestActivity.EXTRA_LAYOUT_RES_ID, R.layout.mockup_activity_druglist);
//					startActivity(intent);
					return true;
				}
			});
		}

		p = findPreference("boot_info");
		if(p != null)
		{
			SpannableString summary = new SpannableString(
					"boot timestamp  : " + RxDroid.getBootTimestamp() + "\n" +
					"BOOT_COMPLETED  : " + Settings.getLong(Keys.BOOT_COMPLETED_TIMESTAMP, 0) + "\n" +
					"update timestamp: " + RxDroid.getLastUpdateTimestamp()
			);
			Util.applyStyle(summary, new TypefaceSpan("monospace"));
			p.setSummary(summary);
		}

		p = findPreference("reset_refill_reminder_date");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					Settings.putDate(Keys.NEXT_REFILL_REMINDER_DATE, null);
					return true;
				}
			});
		}

		p = findPreference("key_debug_create_backup");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					try
					{
						Backup.createBackup(null, "foobar");
					}
					catch(ZipException e)
					{
						Log.w(TAG, e);
						Toast.makeText(PreferencesActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
					return true;
				}
			});
		}

		p = findPreference("dump_build");
		if(p != null)
		{
			p.setOnPreferenceClickListener(new OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					try
					{
						final StringBuilder sb = new StringBuilder();
						final String[] classes = { "android.os.Build", "android.os.Build$VERSION" };

						for(String className : classes)
						{
							Class<?> clazz = Class.forName(className);

							sb.append(clazz.getName() + "\n");
							for(Field f : clazz.getDeclaredFields())
							{
								int m = f.getModifiers();

								if(Modifier.isStatic(m) && Modifier.isPublic(m) &&Modifier.isFinal(m))
								{
									sb.append("  " + f.getName() + ": " + f.get(null) + "\n");
								}
							}
							sb.append("\n");
						}

						Log.d(TAG, sb.toString());
					}
					catch(ClassNotFoundException e)
					{
						Log.w(TAG, e);
					}
					catch(IllegalAccessException e)
					{
						Log.w(TAG, e);
					}

					return true;
				}
			});
		}
	}
}
