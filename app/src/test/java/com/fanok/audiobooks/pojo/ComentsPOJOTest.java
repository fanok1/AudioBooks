package com.fanok.audiobooks.pojo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import org.junit.Test;

public class ComentsPOJOTest {

    @Test
    public void settersStoreNullAsEmpty() {
        ComentsPOJO comment = new ComentsPOJO();
        comment.setName(null);
        comment.setImage(null);
        comment.setDate(null);
        comment.setReting(null);
        comment.setText(null);
        comment.setQuoteName(null);
        comment.setQuoteText(null);
        comment.setChildComents((ArrayList<SubComentsPOJO>) null);
        comment.setChildComents((SubComentsPOJO) null);
        assertEquals("", comment.getName());
        assertEquals("", comment.getImage());
        assertEquals("", comment.getDate());
        assertEquals("", comment.getReting());
        assertEquals("", comment.getText());
        assertEquals("", comment.getQuoteName());
        assertEquals("", comment.getQuoteText());
        assertTrue(comment.getChildComents().isEmpty());
        assertTrue(comment.isEmty());

        SubComentsPOJO reply = new SubComentsPOJO();
        assertEquals("", reply.getParentName());
        reply.setParentName(null);
        assertEquals("", reply.getParentName());
    }

    @Test
    public void aCommentNeedsOnlyText() {
        ComentsPOJO comment = new ComentsPOJO();
        comment.setText("Спасибо!");
        assertFalse(comment.isEmty());
    }
}
