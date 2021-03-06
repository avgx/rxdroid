package at.jclehner.rxdroid;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.UnzipParameters;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import at.jclehner.rxdroid.db.Database;
import at.jclehner.rxdroid.db.DatabaseHelper;
import at.jclehner.rxdroid.util.Util;

public class Backup
{
	private static final String TAG = Backup.class.getSimpleName();

	public static void createBackup(File outFile, String password) throws ZipException
	{
		if(outFile == null)
		{
			final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
			outFile = new File(Environment.getExternalStorageDirectory(),
					"RxDroid/" + sdf.format(new Date()) + ".rxdbak");
		}

		synchronized(Database.LOCK_DATA)
		{
			final ZipFile zip = new ZipFile(outFile);
			final File dataDir = new File(RxDroid.getPackageInfo().applicationInfo.dataDir);

			for(int i = 0; i != FILES.length; ++i)
			{
				final File file = new File(dataDir, FILES[i]);
				if(!file.exists())
					continue;

				final ZipParameters zp = new ZipParameters();
				zp.setRootFolderInZip(new File(FILES[i]).getParent());
				zp.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
				zp.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);

				if(password != null)
				{
					zp.setPassword(password);
					zp.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
					zp.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
					zp.setEncryptFiles(true);
					//zp.setCompressionMethod(Zip4jConstants.COMP_AES_ENC);
				}

				zip.addFile(file, zp);
			}

			zip.setComment("rxdbak1:" + System.currentTimeMillis() + ":DBv" + DatabaseHelper.DB_VERSION);
		}
	}

	public static void restoreBackup(ZipFile zipFile) throws ZipException
	{
		synchronized(Database.LOCK_DATA)
		{
			zipFile.extractAll(RxDroid.getPackageInfo().applicationInfo.dataDir);
			Settings.init(true);
			Database.reload(RxDroid.getContext());
		}

		NotificationReceiver.rescheduleAlarmsAndUpdateNotification(false);
	}

	private static final String[] FILES = {
			"databases/" + DatabaseHelper.DB_NAME,
			"shared_prefs/at.jclehner.rxdroid_preferences.xml",
			"shared_prefs/showcase_internal.xml"
	};
}
