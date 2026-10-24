package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class JsonSliceTest {

    @Test
    public void cutsTheBalancedLiteralIgnoringBracketsInStrings() {
        String script = "var p = new Player(7, [{\"t\":\"a ] } [\",\"u\":'x\\'] '}], [9]);\nvar later = [1];";
        assertEquals("[{\"t\":\"a ] } [\",\"u\":'x\\'] '}]", JsonSlice.after(script, "new Player(", '['));
        assertEquals("{\"a\":{\"b\":[1,{\"c\":2}]}}", JsonSlice.from("x({\"a\":{\"b\":[1,{\"c\":2}]}});y()", 0, '{'));
    }

    @Test
    public void bracketInsideAStringArgumentIsNotTheLiteral() {
        assertEquals("[{\"a\":1}]", JsonSlice.after("new BookPlayer(\"book[1]\", [{\"a\":1}]);", "new BookPlayer(", '['));
    }

    @Test
    public void commentsAreStrippedButStringsKept() {
        assertEquals("var u = \"http://x/*y*/\";   var v = 'a//b'; \n",
                JsonSlice.stripComments("var u = \"http://x/*y*/\"; /* old */ var v = 'a//b'; // tail\n"));
        assertEquals("[2]", JsonSlice.after("/* new P([1]); */ new P([2]);", "new P(", '['));
        assertThrows(IllegalStateException.class, () -> JsonSlice.after("// new P([1]);", "new P(", '['));
    }

    @Test
    public void missingMarkerOrUnbalancedLiteralIsIllegalState() {
        assertThrows(IllegalStateException.class, () -> JsonSlice.after("domReady(function(){})", "new Player(", '['));
        assertThrows(IllegalStateException.class, () -> JsonSlice.after("new Player(1, [{\"a\":1}", "new Player(", '['));
        assertThrows(IllegalStateException.class, () -> JsonSlice.after("new Player(1);", "new Player(", '['));
    }
}
