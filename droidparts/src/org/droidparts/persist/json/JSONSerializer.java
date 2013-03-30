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
package org.droidparts.persist.json;

import static org.droidparts.reflect.FieldSpecBuilder.getJsonKeySpecs;
import static org.droidparts.reflect.util.ReflectionUtils.getFieldVal;
import static org.droidparts.reflect.util.ReflectionUtils.instantiate;
import static org.droidparts.reflect.util.ReflectionUtils.setFieldVal;
import static org.droidparts.reflect.util.TypeHelper.isArray;
import static org.droidparts.reflect.util.TypeHelper.isCollection;
import static org.droidparts.reflect.util.TypeHelper.isDate;
import static org.droidparts.reflect.util.TypeHelper.isEnum;
import static org.droidparts.reflect.util.TypeHelper.isModel;
import static org.droidparts.reflect.util.TypeHelper.isUUID;
import static org.droidparts.reflect.util.TypeHelper.toTypeArr;
import static org.json.JSONObject.NULL;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import org.droidparts.inject.Injector;
import org.droidparts.model.Model;
import org.droidparts.reflect.ann.FieldSpec;
import org.droidparts.reflect.ann.json.KeyAnn;
import org.droidparts.reflect.type.AbstractHandler;
import org.droidparts.reflect.util.TypeHandlerRegistry;
import org.droidparts.util.Arrays2;
import org.droidparts.util.L;
import org.droidparts.util.PersistUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

public class JSONSerializer<ModelType extends Model> {

	// ASCII GS (group separator), '->' for readability
	public static final String __ = "->" + (char) 29;

	private final Context ctx;
	private final Class<ModelType> cls;

	public JSONSerializer(Context ctx, Class<ModelType> cls) {
		this(cls, ctx);
		Injector.get().inject(ctx, this);
	}

	private JSONSerializer(Class<ModelType> cls, Context ctx) {
		this.ctx = ctx.getApplicationContext();
		this.cls = cls;
	}

	public Context getContext() {
		return ctx;
	}

	public JSONObject serialize(ModelType item) throws JSONException {
		JSONObject obj = new JSONObject();
		for (FieldSpec<KeyAnn> spec : getJsonKeySpecs(cls)) {
			readFromModelAndPutToJSON(item, spec, obj, spec.ann.name);
		}
		return obj;
	}

	public ModelType deserialize(JSONObject obj) throws JSONException {
		ModelType model = instantiate(cls);
		for (FieldSpec<KeyAnn> spec : getJsonKeySpecs(cls)) {
			readFromJSONAndSetFieldVal(model, spec, obj, spec.ann.name);
		}
		return model;
	}

	public JSONArray serialize(Collection<ModelType> items)
			throws JSONException {
		JSONArray arr = new JSONArray();
		for (ModelType item : items) {
			arr.put(serialize(item));
		}
		return arr;
	}

	public ArrayList<ModelType> deserialize(JSONArray arr) throws JSONException {
		ArrayList<ModelType> list = new ArrayList<ModelType>();
		for (int i = 0; i < arr.length(); i++) {
			list.add(deserialize(arr.getJSONObject(i)));
		}
		return list;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void putToJSONObject(JSONObject obj, String key,
			Class<?> valType, Class<?> arrCollItemType, Object val)
			throws Exception {
		if (val == null) {
			obj.put(key, NULL);
			return;
		}
		AbstractHandler<?> handler = TypeHandlerRegistry.get(valType);
		if (handler != null) {
			handler.putToJSONObject(obj, key, val);
			return;
		}
		// TODO
		if (isModel(valType)) {
			JSONObject obj2 = subSerializer(valType).serialize((Model) val);
			obj.put(key, obj2);
		} else if (isArray(valType) || isCollection(valType)) {
			final ArrayList<Object> list = new ArrayList<Object>();
			if (isArray(valType)) {
				list.addAll(Arrays.asList(Arrays2.toObjectArr(val)));
			} else {
				list.addAll((Collection<?>) val);
			}
			JSONArray jArr = new JSONArray();
			if (isModel(arrCollItemType)) {
				JSONSerializer serializer = subSerializer(arrCollItemType);
				jArr = serializer.serialize(list);
			} else {
				boolean isDate = isDate(arrCollItemType);
				boolean toString = isUUID(arrCollItemType)
						|| isEnum(arrCollItemType);
				for (Object o : list) {
					if (isDate) {
						o = ((Date) o).getTime();
					} else if (toString) {
						o = o.toString();
					}
					jArr.put(o);
				}
			}
			obj.put(key, jArr);
		} else {
			throw new IllegalArgumentException("Unsupported class: " + valType);
		}
	}

	protected Object readFromJSON(Class<?> fieldType, Class<?> arrCollItemType,
			JSONObject obj, String key) throws Exception {
		Object jsonVal = obj.get(key);
		if (NULL.equals(jsonVal)) {
			return jsonVal;
		}

		AbstractHandler<?> handler = TypeHandlerRegistry.get(fieldType);
		Exception e = null;
		if (handler != null) {
			try {
				return handler.readFromJSON(fieldType, obj, key);
			} catch (Exception ex) {
				e = ex;
			}
		}

		if (isModel(fieldType)) {
			return subSerializer(fieldType).deserialize((JSONObject) jsonVal);
		} else if (isArray(fieldType) || isCollection(fieldType)) {
			String strVal = String.valueOf(jsonVal);
			JSONArray jArr = (jsonVal instanceof JSONArray) ? (JSONArray) jsonVal
					: new JSONArray(strVal);
			boolean isArr = isArray(fieldType);
			Object[] arr = null;
			Collection<Object> coll = null;
			if (isArr) {
				arr = new Object[jArr.length()];
			} else {
				@SuppressWarnings("unchecked")
				Class<? extends Collection<Object>> cl = (Class<? extends Collection<Object>>) fieldType;
				coll = instantiate(cl);
			}
			boolean isModel = isModel(arrCollItemType);
			JSONSerializer<Model> serializer = null;
			if (isModel) {
				serializer = subSerializer(arrCollItemType);
			}
			for (int i = 0; i < jArr.length(); i++) {
				Object obj1 = jArr.get(i);
				if (isModel) {
					obj1 = serializer.deserialize((JSONObject) obj1);
				}
				if (isArr) {
					arr[i] = obj1;
				} else {
					coll.add(obj1);
				}
			}
			if (isArr) {
				if (isModel) {
					Object modelArr = Array.newInstance(arrCollItemType,
							arr.length);
					for (int i = 0; i < arr.length; i++) {
						Array.set(modelArr, i, arr[i]);
					}
					return modelArr;
				} else {
					String[] arr2 = new String[arr.length];
					for (int i = 0; i < arr.length; i++) {
						arr2[i] = arr[i].toString();
					}
					return toTypeArr(arrCollItemType, arr2);
				}
			} else {
				return coll;
			}
		}
		throw e;
	}

	protected boolean hasNonNull(JSONObject obj, String key)
			throws JSONException {
		return PersistUtils.hasNonNull(obj, key);
	}

	private void readFromModelAndPutToJSON(ModelType item,
			FieldSpec<KeyAnn> spec, JSONObject obj, String key)
			throws JSONException {
		Pair<String, String> keyParts = getNestedKeyParts(key);
		if (keyParts != null) {
			String subKey = keyParts.first;
			JSONObject subObj;
			if (hasNonNull(obj, subKey)) {
				subObj = obj.getJSONObject(subKey);
			} else {
				subObj = new JSONObject();
				obj.put(subKey, subObj);
			}
			readFromModelAndPutToJSON(item, spec, subObj, keyParts.second);
		} else {
			Object columnVal = getFieldVal(item, spec.field);
			try {
				putToJSONObject(obj, key, spec.field.getType(),
						spec.arrCollItemType, columnVal);
			} catch (Exception e) {
				if (spec.ann.optional) {
					L.w("Failded to serialize %s.%s: %s.", cls.getSimpleName(),
							spec.field.getName(), e.getMessage());
				} else {
					throw new JSONException(Log.getStackTraceString(e));
				}
			}
		}
	}

	private void readFromJSONAndSetFieldVal(ModelType model,
			FieldSpec<KeyAnn> spec, JSONObject obj, String key)
			throws JSONException {
		Pair<String, String> keyParts = getNestedKeyParts(key);
		if (keyParts != null) {
			String subKey = keyParts.first;
			if (hasNonNull(obj, subKey)) {
				JSONObject subObj = obj.getJSONObject(subKey);
				readFromJSONAndSetFieldVal(model, spec, subObj, keyParts.second);
			} else {
				throwIfRequired(spec);
			}
		} else if (obj.has(key)) {
			try {
				Object val = readFromJSON(spec.field.getType(),
						spec.arrCollItemType, obj, key);
				if (!NULL.equals(val)) {
					setFieldVal(model, spec.field, val);
				} else {
					L.i("Received NULL '%s', skipping.", spec.ann.name);
				}
			} catch (Exception e) {
				if (spec.ann.optional) {
					L.w("Failed to deserialize '%s': %s.", spec.ann.name,
							e.getMessage());
				} else {
					throw new JSONException(Log.getStackTraceString(e));
				}
			}
		} else {
			throwIfRequired(spec);
		}
	}

	private Pair<String, String> getNestedKeyParts(String key) {
		int firstSep = key.indexOf(__);
		if (firstSep != -1) {
			String subKey = key.substring(0, firstSep);
			String leftKey = key.substring(firstSep + __.length());
			Pair<String, String> pair = Pair.create(subKey, leftKey);
			return pair;
		} else {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private JSONSerializer<Model> subSerializer(Class<?> cls) {
		return new JSONSerializer<Model>((Class<Model>) cls, ctx);
	}

	private void throwIfRequired(FieldSpec<KeyAnn> spec) throws JSONException {
		if (!spec.ann.optional) {
			throw new JSONException("Required key '" + spec.ann.name
					+ "' not present.");
		}
	}

}