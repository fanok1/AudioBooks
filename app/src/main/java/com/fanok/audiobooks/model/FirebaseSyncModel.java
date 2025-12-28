package com.fanok.audiobooks.model;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fanok.audiobooks.room.AppDatabase;
import com.fanok.audiobooks.room.AudioEntity;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.room.FavoriteEntity;
import com.fanok.audiobooks.room.HistoryEntity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirebaseSyncModel {
    private static final String TAG = "FirebaseSyncModel";
    private final AppDatabase mDatabase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<DatabaseReference, ValueEventListener> activeListeners = new HashMap<>();

    public FirebaseSyncModel(Context context) {
        mDatabase = AppDatabase.getDatabase(context);
    }

    public void destroy(){
        if(executor!=null&&!executor.isShutdown()){
            executor.shutdown();
        }
    }

    public void markLocalDataAsDirty() {
        if(executor.isShutdown()) return;
        executor.execute(() -> {
            mDatabase.favoriteDao().markAllAsNeedSync();
            mDatabase.historyDao().markAllAsNeedSync();
            mDatabase.audioDao().markAllAsNeedSync();
            mDatabase.booksAudioDao().markAllAsNeedSync();
        });
    }

    public void uploadLocalChanges() {
        if(executor.isShutdown()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());

        executor.execute(() -> {
            uploadFavorites(userRef.child("favorite"));
            uploadHistory(userRef.child("history"));
            uploadAudio(userRef.child("audio"));
            uploadBooksAudio(userRef.child("books_audio"));
        });
    }

    public void startListening() {
        stopListening(); // Ensure no duplicate listeners
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());

        // --- Connection Listener ---
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        ValueEventListener connectedListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                Log.d(TAG, "Firebase connection status: " + connected);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        connectedRef.addValueEventListener(connectedListener);
        activeListeners.put(connectedRef, connectedListener);
        // ---------------------------

        listenToFavorites(userRef.child("favorite"));
        listenToHistory(userRef.child("history"));
        listenToAudio(userRef.child("audio"));
        listenToBooksAudio(userRef.child("books_audio"));
    }

    public void stopListening() {
        for (Map.Entry<DatabaseReference, ValueEventListener> entry : activeListeners.entrySet()) {
            entry.getKey().removeEventListener(entry.getValue());
        }
        activeListeners.clear();
    }

    private void listenToFavorites(DatabaseReference ref) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(executor.isShutdown()) return;
                executor.execute(() -> {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            FavoriteEntity serverEntity = child.getValue(FavoriteEntity.class);
                            if (serverEntity == null) continue;

                            FavoriteEntity localEntity = mDatabase.favoriteDao().getRawByUrl(serverEntity.urlBook);
                            if (localEntity == null) {
                                if (!serverEntity.deleted) {
                                    serverEntity.id = 0;
                                    serverEntity.needSync = false;
                                    mDatabase.favoriteDao().insert(serverEntity);
                                }
                            } else {
                                if (serverEntity.updatedAt > localEntity.updatedAt) {
                                    serverEntity.id = localEntity.id;
                                    serverEntity.needSync = false;
                                    mDatabase.favoriteDao().insert(serverEntity);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing favorite snapshot", e);
                        }
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Favorite listener cancelled", error.toException());
            }
        };
        ref.addValueEventListener(listener);
        activeListeners.put(ref, listener);
    }

    private void listenToHistory(DatabaseReference ref) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(executor.isShutdown()) return;
                executor.execute(() -> {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            HistoryEntity serverEntity = child.getValue(HistoryEntity.class);
                            if (serverEntity == null) continue;

                            HistoryEntity localEntity = mDatabase.historyDao().getRawByUrl(serverEntity.urlBook);
                            if (localEntity == null) {
                                if (!serverEntity.deleted) {
                                    serverEntity.id = 0;
                                    serverEntity.needSync = false;
                                    mDatabase.historyDao().insert(serverEntity);
                                }
                            } else {
                                if (serverEntity.updatedAt > localEntity.updatedAt) {
                                    serverEntity.id = localEntity.id;
                                    serverEntity.needSync = false;
                                    mDatabase.historyDao().insert(serverEntity);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing history snapshot", e);
                        }
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "History listener cancelled", error.toException());
            }
        };
        ref.addValueEventListener(listener);
        activeListeners.put(ref, listener);
    }

    private void listenToAudio(DatabaseReference ref) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(executor.isShutdown()) return;
                executor.execute(() -> {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        try {
                            AudioEntity serverEntity = child.getValue(AudioEntity.class);
                            if (serverEntity == null) continue;

                            AudioEntity localEntity = mDatabase.audioDao().getRawByUrl(serverEntity.urlBook);
                            if (localEntity == null) {
                                if (!serverEntity.deleted) {
                                    serverEntity.id = 0;
                                    serverEntity.needSync = false;
                                    mDatabase.audioDao().insert(serverEntity);
                                }
                            } else {
                                if (serverEntity.updatedAt > localEntity.updatedAt) {
                                    serverEntity.id = localEntity.id;
                                    serverEntity.needSync = false;
                                    mDatabase.audioDao().insert(serverEntity);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing audio snapshot", e);
                        }
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Audio listener cancelled", error.toException());
            }
        };
        ref.addValueEventListener(listener);
        activeListeners.put(ref, listener);
    }

    private void listenToBooksAudio(DatabaseReference ref) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(executor.isShutdown()) return;
                executor.execute(() -> {
                    for (DataSnapshot bookSnapshot : snapshot.getChildren()) {
                        for (DataSnapshot audioSnapshot : bookSnapshot.getChildren()) {
                            try {
                                BooksAudioEntity serverEntity = audioSnapshot.getValue(BooksAudioEntity.class);
                                if (serverEntity == null) continue;

                                BooksAudioEntity localEntity = mDatabase.booksAudioDao().getRawByUrlAndAudioUrl(serverEntity.urlBook, serverEntity.urlAudio);
                                if (localEntity == null) {
                                    if (!serverEntity.deleted) {
                                        serverEntity.id = 0;
                                        serverEntity.needSync = false;
                                        mDatabase.booksAudioDao().insert(serverEntity);
                                    }
                                } else {
                                    if (serverEntity.updatedAt > localEntity.updatedAt) {
                                        serverEntity.id = localEntity.id;
                                        serverEntity.needSync = false;
                                        mDatabase.booksAudioDao().insert(serverEntity);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing books_audio snapshot", e);
                            }
                        }
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "BooksAudio listener cancelled", error.toException());
            }
        };
        ref.addValueEventListener(listener);
        activeListeners.put(ref, listener);
    }

    private void uploadFavorites(DatabaseReference ref) {
        List<FavoriteEntity> toSync = mDatabase.favoriteDao().getEntitiesToSync();
        if (toSync.isEmpty()) return;
        Log.d(TAG, "Uploading " + toSync.size() + " favorites.");
        for (FavoriteEntity entity : toSync) {
            String key = encodeUrl(entity.urlBook);
            ref.child(key).setValue(entity)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Favorite uploaded: " + entity.urlBook);
                        if(executor.isShutdown()) return;
                        executor.execute(() -> mDatabase.favoriteDao().markAsSynced(entity.urlBook, entity.updatedAt));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload favorite: " + entity.urlBook, e));
        }
    }

    private void uploadHistory(DatabaseReference ref) {
        List<HistoryEntity> toSync = mDatabase.historyDao().getEntitiesToSync();
        if (toSync.isEmpty()) return;
        Log.d(TAG, "Uploading " + toSync.size() + " history items.");
        for (HistoryEntity entity : toSync) {
            String key = encodeUrl(entity.urlBook);
            ref.child(key).setValue(entity)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "History uploaded: " + entity.urlBook);
                        if(executor.isShutdown()) return;
                        executor.execute(() -> mDatabase.historyDao().markAsSynced(entity.urlBook, entity.updatedAt));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload history: " + entity.urlBook, e));
        }
    }

    private void uploadAudio(DatabaseReference ref) {
        List<AudioEntity> toSync = mDatabase.audioDao().getEntitiesToSync();
        if (toSync.isEmpty()) return;
        Log.d(TAG, "Uploading " + toSync.size() + " audio progress items.");
        for (AudioEntity entity : toSync) {
            String key = encodeUrl(entity.urlBook);
            ref.child(key).setValue(entity)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Audio progress uploaded: " + entity.urlBook);
                        if(executor.isShutdown()) return;
                        executor.execute(() -> mDatabase.audioDao().markAsSynced(entity.urlBook, entity.updatedAt));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload audio progress: " + entity.urlBook, e));
        }
    }

    private void uploadBooksAudio(DatabaseReference ref) {
        List<BooksAudioEntity> toSync = mDatabase.booksAudioDao().getEntitiesToSync();
        if (toSync.isEmpty()) return;
        Log.d(TAG, "Uploading " + toSync.size() + " book audio lists.");
        for (BooksAudioEntity entity : toSync) {
            String bookKey = encodeUrl(entity.urlBook);
            String audioKey = encodeUrl(entity.urlAudio);
            if (audioKey.isEmpty()) audioKey = "default";
            ref.child(bookKey).child(audioKey).setValue(entity)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Book audio list uploaded: " + entity.urlBook);
                        if(executor.isShutdown()) return;
                        executor.execute(() -> mDatabase.booksAudioDao().markAsSynced(entity.urlBook, entity.urlAudio, entity.updatedAt));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload book audio list: " + entity.urlBook, e));
        }
    }

    private String encodeUrl(String url) {
        if (url == null) return "";
        return Base64.encodeToString(url.getBytes(), Base64.NO_WRAP).replace("/", "_").replace("+", "-").replace("=", "");
    }
}
