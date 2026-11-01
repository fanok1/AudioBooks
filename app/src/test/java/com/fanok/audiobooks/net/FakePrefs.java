package com.fanok.audiobooks.net;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** In-memory SharedPreferences for JVM tests; a wrongly typed read throws ClassCastException like the real one. */
public final class FakePrefs implements SharedPreferences {

    private static final Object REMOVED = new Object();
    private final Map<String, Object> values = new HashMap<>();

    @Override
    public synchronized Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public synchronized String getString(String key, String defValue) {
        return values.containsKey(key) ? (String) values.get(key) : defValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        return values.containsKey(key) ? (Set<String>) values.get(key) : defValues;
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        return values.containsKey(key) ? (Integer) values.get(key) : defValue;
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        return values.containsKey(key) ? (Long) values.get(key) : defValue;
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        return values.containsKey(key) ? (Float) values.get(key) : defValue;
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        return values.containsKey(key) ? (Boolean) values.get(key) : defValue;
    }

    @Override
    public synchronized boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    }

    private final class FakeEditor implements Editor {
        private final Map<String, Object> changes = new LinkedHashMap<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            changes.put(key, value == null ? REMOVED : value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            changes.put(key, values == null ? REMOVED : new HashSet<>(values));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            changes.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            changes.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            changes.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            changes.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            changes.put(key, REMOVED);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            synchronized (FakePrefs.this) {
                if (clear) {
                    values.clear();
                }
                for (Map.Entry<String, Object> change : changes.entrySet()) {
                    if (change.getValue() == REMOVED) {
                        values.remove(change.getKey());
                    } else {
                        values.put(change.getKey(), change.getValue());
                    }
                }
            }
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
