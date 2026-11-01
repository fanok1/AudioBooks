package com.fanok.audiobooks.pojo;

import androidx.annotation.Nullable;
import java.util.ArrayList;

public class ComentsPOJO extends ContentParentPOJO {
    private final ArrayList<SubComentsPOJO> childComents;

    public ComentsPOJO() {
        childComents = new ArrayList<>();
    }

    public ArrayList<SubComentsPOJO> getChildComents() {
        return childComents;
    }

    public void setChildComents(@Nullable ArrayList<SubComentsPOJO> childComents) {
        if (childComents != null) {
            this.childComents.addAll(childComents);
        }
    }

    public void setChildComents(@Nullable SubComentsPOJO childComents) {
        if (childComents != null) {
            this.childComents.add(childComents);
        }
    }
}
