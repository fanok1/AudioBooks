package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

import java.util.ArrayList;

public class ComentsPOJO extends ContentParentPOJO {
    private ArrayList<SubComentsPOJO> childComents;

    public ComentsPOJO() {
        childComents = new ArrayList<>();
    }

    public ArrayList<SubComentsPOJO> getChildComents() {
        return childComents;
    }

    public void setChildComents(@NonNull ArrayList<SubComentsPOJO> childComents) {
        this.childComents.addAll(childComents);
    }

    public void setChildComents(@NonNull SubComentsPOJO childComents) {
        this.childComents.add(childComents);
    }
}
