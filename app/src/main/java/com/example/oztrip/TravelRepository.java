package com.example.oztrip;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

public class TravelRepository {
    private final FirebaseFirestore db;

    public TravelRepository() {
        db = FirebaseFirestore.getInstance();
    }

    private String getUserId() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return null;
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void loadAllLists(OnDataLoadedListener listener) {
        String userId = getUserId();
        if (userId == null) {
            if (listener != null) listener.onError("Not signed in");
            return;
        }
        db.collection("travel_lists")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<TravelList> lists = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        TravelList list = TravelList.fromMap(doc.getId(), doc.getData());
                        if (list != null) lists.add(list);
                    }
                    listener.onLoaded(lists);
                })
                .addOnFailureListener(e -> listener.onError(e.getMessage()));
    }

    public void saveAllLists(List<TravelList> lists, OnSaveListener listener) {
        String userId = getUserId();
        if (userId == null) {
            if (listener != null) listener.onError("Not signed in");
            return;
        }
        db.collection("travel_lists")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                    for (TravelList list : lists) {
                        Map<String, Object> data = list.toMap();
                        data.put("userId", userId);
                        db.collection("travel_lists").add(data);
                    }
                    if (listener != null) listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e.getMessage());
                });
    }

    public interface OnDataLoadedListener {
        void onLoaded(List<TravelList> lists);
        void onError(String error);
    }

    public interface OnSaveListener {
        void onSuccess();
        void onError(String error);
    }
}