package com.prashant.safdemo;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
	
	private static final int READ_REQUEST_CODE = 500;
	
	private static final int PERMISSIONS_REQUEST_STORAGE = 123;
	
	TextView filePath;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		filePath = findViewById(R.id.filePath);
		findViewById(R.id.buttonPick).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				pickFile();
				
			}
		});
	}
	
	private void pickFile() {
		// Here, thisActivity is the current activity
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
					PERMISSIONS_REQUEST_STORAGE);
			
			
		} else {
			// Permission has already been granted
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			// Set mimetype
			intent.setType("*/*");
			startActivityForResult(intent, READ_REQUEST_CODE);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			Uri uri = data.getData();
			InputStream inputStream;
			String mimeType = getMimeTypeFromFile(uri);
			String fileName = getFileName(uri);
			fileName = fileName == null ? "Unknown." + mimeType : fileName;
			try {
				if (isVirtualFile(uri)) {
					inputStream = getInputStreamForVirtualFile(uri, mimeType);
				} else {
					inputStream = getInputStreamFromUri(uri);
				}
				File file = writeInputStreamToFile(inputStream, fileName);
				if (file != null) {
					filePath.setText(file.getPath());
				}
			} catch (Exception e) {
				e.printStackTrace();
				if (e instanceof FileNotFoundException) {
					Toast.makeText(this, "File not found", Toast.LENGTH_LONG).show();
				}
			}
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[],
	                                       int[] grantResults) {
		switch (requestCode) {
			case PERMISSIONS_REQUEST_STORAGE: {
				// If request is cancelled, the result arrays are empty.
				pickFile();
			}
			
		}
	}
	
	private String getMimeTypeFromFile(Uri uri) {
		ContentResolver cR = getContentResolver();
		MimeTypeMap mime = MimeTypeMap.getSingleton();
		return mime.getExtensionFromMimeType(cR.getType(uri));
	}
	
	public String getFileName(Uri uri) {
		String displayName = null;
		// The query, since it only applies to a single document, will only return
		// one row. There's no need to filter, sort, or select fields, since we want
		// all fields for one document.
		Cursor cursor = getContentResolver().query(uri, null, null, null, null, null);
		
		try {
			// moveToFirst() returns false if the cursor has 0 rows.  Very handy for
			// "if there's anything to look at, look at it" conditionals.
			if (cursor != null && cursor.moveToFirst()) {
				
				// Note it's called "Display Name".  This is
				// provider-specific, and might not necessarily be the file name.
				displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns
						.DISPLAY_NAME));
				
				int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
				// If the size is unknown, the value stored is null.  But since an
				// int can't be null in Java, the behavior is implementation-specific,
				// which is just a fancy term for "unpredictable".  So as
				// a rule, check if it's null before assigning to an int.  This will
				// happen often:  The storage API allows for remote files, whose
				// size might not be locally known.
				String size = null;
				if (!cursor.isNull(sizeIndex)) {
					// Technically the column stores an int, but cursor.getString()
					// will do the conversion automatically.
					size = cursor.getString(sizeIndex);
				} else {
					size = "Unknown";
				}
			}
		} finally {
			cursor.close();
		}
		return displayName;
	}
	
	private boolean isVirtualFile(Uri uri) {
		if (!DocumentsContract.isDocumentUri(this, uri)) {
			return false;
		}
		
		Cursor cursor = getContentResolver().query(uri,
				new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null);
		
		int flags = 0;
		if (cursor.moveToFirst()) {
			flags = cursor.getInt(0);
		}
		cursor.close();
		
		return (flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0;
	}
	
	private InputStream getInputStreamForVirtualFile(Uri uri,
	                                                 String mimeTypeFilter) throws IOException {
		
		ContentResolver resolver = getContentResolver();
		
		String[] openableMimeTypes = resolver.getStreamTypes(uri, mimeTypeFilter);
		
		if (openableMimeTypes == null || openableMimeTypes.length < 1) {
			throw new FileNotFoundException();
		}
		
		return resolver.openTypedAssetFileDescriptor(uri, openableMimeTypes[0],
				null).createInputStream();
	}
	
	private InputStream getInputStreamFromUri(Uri uri) throws IOException {
		//		InputStream inputStream = getContentResolver().openInputStream(uri);
		return getContentResolver().openInputStream(uri);
	}
	
	private File writeInputStreamToFile(InputStream inputStream, String fileName) {
		File file = null;
		try {
			file = new File(Environment.getExternalStorageDirectory(), fileName);
			if (file.exists()) {
				file.delete();
			}
			file.createNewFile();
			OutputStream output = new FileOutputStream(file);
			try {
				byte[] buffer = new byte[4 * 1024]; // or other buffer size
				int read;
				
				while ((read = inputStream.read(buffer)) != -1) {
					output.write(buffer, 0, read);
				}
				
				output.flush();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				output.close();
			}
			inputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file;
	}
}
