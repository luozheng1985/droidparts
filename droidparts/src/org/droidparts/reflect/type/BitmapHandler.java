/**
 * Copyright 2013 Alex Yanchenko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.droidparts.reflect.type;

import java.io.ByteArrayOutputStream;

import org.droidparts.reflect.util.TypeHelper;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;

public class BitmapHandler extends TypeHandler<Bitmap> {

	@Override
	public boolean canHandle(Class<?> cls) {
		return TypeHelper.isBitmap(cls);
	}

	@Override
	public String getDBColumnType() {
		return BLOB;
	}

	@Override
	protected <V> Bitmap parseFromString(Class<Bitmap> valType,
			Class<V> arrCollItemType, String str) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <V> void putToContentValues(Class<Bitmap> valueType,
			Class<V> arrCollItemType, ContentValues cv, String key, Bitmap val) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		val.compress(CompressFormat.PNG, 0, baos);
		cv.put(key, baos.toByteArray());
	}

	@Override
	public <V> Bitmap readFromCursor(Class<Bitmap> valType,
			Class<V> arrCollItemType, Cursor cursor, int columnIndex) {
		byte[] arr = cursor.getBlob(columnIndex);
		return BitmapFactory.decodeByteArray(arr, 0, arr.length);
	}

	@Override
	public Object parseTypeArr(Class<Bitmap> valType, String[] arr) {
		throw new UnsupportedOperationException();
	}

}