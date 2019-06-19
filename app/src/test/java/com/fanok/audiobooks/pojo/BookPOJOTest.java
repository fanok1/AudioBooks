package com.fanok.audiobooks.pojo;

import org.junit.Assert;
import org.junit.Test;

public class BookPOJOTest {

    @Test
    public void setPhoto() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "aodhwaoud";
        final String failUrl2 = "";
        final String failUrl3 = "http://";
        final String failUrl4 = "https://audioknigi.club/uploads/media/topic/2019/06/15/16/";
        final String trueUrl =
                "https://audioknigi.club/uploads/media/topic/2019/06/15/16/preview"
                        + "/65eb762d5dd61ae0ac33_252x350crop.jpg";

        try {
            mBookPOJO.setPhoto(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setPhoto(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setPhoto(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setPhoto(failUrl4);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setPhoto(trueUrl);
        Assert.assertEquals(mBookPOJO.getPhoto(), trueUrl);
    }

    @Test
    public void setAutor() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String nameFail = "";
        final String nameTrue = "Not Empty";
        try {
            mBookPOJO.setAutor(nameFail);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setAutor(nameTrue);
        Assert.assertEquals(mBookPOJO.getAutor(), nameTrue);
    }

    @Test
    public void setArtist() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String nameFail = "";
        final String nameTrue = "Not Empty";
        try {
            mBookPOJO.setArtist(nameFail);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setArtist(nameTrue);
        Assert.assertEquals(mBookPOJO.getArtist(), nameTrue);
    }

    @Test
    public void setUrlArtist() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "aodhwaoud";
        final String failUrl2 = "";
        final String failUrl3 = "http://";
        final String trueUrl = "https://audioknigi.club/uploads/media/topic/2019/06/15/16/";
        final String trueUrl2 =
                "https://audioknigi.club/uploads/media/topic/2019/06/15/16/dhuiawdgaw.php";

        try {
            mBookPOJO.setUrlArtist(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlArtist(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlArtist(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setUrlArtist(trueUrl);
        Assert.assertEquals(mBookPOJO.getUrlArtist(), trueUrl);
        mBookPOJO.setUrlArtist(trueUrl2);
        Assert.assertEquals(mBookPOJO.getUrlArtist(), trueUrl2);
    }

    @Test
    public void setUrlAutor() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "aodhwaoud";
        final String failUrl2 = "";
        final String failUrl3 = "http://";
        final String trueUrl = "https://audioknigi.club/uploads/media/topic/2019/06/15/16/";
        final String trueUrl2 =
                "https://audioknigi.club/uploads/media/topic/2019/06/15/16/dhuiawdgaw.php";

        try {
            mBookPOJO.setUrlAutor(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlAutor(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlAutor(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setUrlAutor(trueUrl);
        Assert.assertEquals(mBookPOJO.getUrlAutor(), trueUrl);
        mBookPOJO.setUrlAutor(trueUrl2);
        Assert.assertEquals(mBookPOJO.getUrlAutor(), trueUrl2);
    }

    @Test
    public void setTime() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String nameFail = "";
        final String nameTrue = "Not Empty";
        try {
            mBookPOJO.setTime(nameFail);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setTime(nameTrue);
        Assert.assertEquals(mBookPOJO.getTime(), nameTrue);
    }

    @Test
    public void setSeries() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String nameFail = "";
        final String nameTrue = "Not Empty";
        try {
            mBookPOJO.setSeries(nameFail);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setSeries(nameTrue);
        Assert.assertEquals(mBookPOJO.getSeries(), nameTrue);
    }

    @Test
    public void setGenre() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String nameFail = "";
        final String nameTrue = "Not Empty";
        try {
            mBookPOJO.setGenre(nameFail);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setGenre(nameTrue);
        Assert.assertEquals(mBookPOJO.getGenre(), nameTrue);
    }

    @Test
    public void setUrlGenre() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "aodhwaoud";
        final String failUrl2 = "";
        final String failUrl3 = "http://";
        final String trueUrl = "https://audioknigi.club/uploads/media/topic/2019/06/15/16/";
        final String trueUrl2 =
                "https://audioknigi.club/uploads/media/topic/2019/06/15/16/dhuiawdgaw.php";

        try {
            mBookPOJO.setUrlGenre(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlGenre(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlGenre(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setUrlGenre(trueUrl);
        Assert.assertEquals(mBookPOJO.getUrlGenre(), trueUrl);
        mBookPOJO.setUrlGenre(trueUrl2);
        Assert.assertEquals(mBookPOJO.getUrlGenre(), trueUrl2);
    }

    @Test
    public void setUrlSeries() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "aodhwaoud";
        final String failUrl2 = "";
        final String failUrl3 = "http://";
        final String trueUrl = "https://audioknigi.club/uploads/media/topic/2019/06/15/16/";
        final String trueUrl2 =
                "https://audioknigi.club/uploads/media/topic/2019/06/15/16/dhuiawdgaw.php";

        try {
            mBookPOJO.setUrlSeries(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlSeries(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrlSeries(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setUrlSeries(trueUrl);
        Assert.assertEquals(mBookPOJO.getUrlSeries(), trueUrl);
        mBookPOJO.setUrlSeries(trueUrl2);
        Assert.assertEquals(mBookPOJO.getUrlSeries(), trueUrl2);
    }


    @Test
    public void setReting() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "9";
        final String failUrl2 = "-0";
        final String failUrl3 = "dwad";
        final String trueUrl = "+5";
        final String trueUrl2 = "0";

        try {
            mBookPOJO.setReting(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setReting(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setReting(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setReting(trueUrl);
        Assert.assertEquals(mBookPOJO.getReting(), trueUrl);
        mBookPOJO.setReting(trueUrl2);
        Assert.assertEquals(mBookPOJO.getReting(), trueUrl2);
    }

    @Test
    public void setFavorite() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final int incorect1 = -5;
        final int corect1 = 0;
        final int corect2 = 10;
        try {
            mBookPOJO.setFavorite(incorect1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setFavorite(corect1);
        Assert.assertEquals(mBookPOJO.getFavorite(), corect1);

        mBookPOJO.setFavorite(corect2);
        Assert.assertEquals(mBookPOJO.getFavorite(), corect2);


    }

    @Test
    public void setComents() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String incorect2 = "dwadsw";
        final String corect1 = "2 dawdwads";
        final String corect2 = "10";
        try {
            mBookPOJO.setComents(incorect2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setComents(corect1);
        Assert.assertEquals(mBookPOJO.getComents(), 2);

        mBookPOJO.setComents(corect2);
        Assert.assertEquals(mBookPOJO.getComents(), 10);
    }

    @Test
    public void setName() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String nameFail = "";
        final String nameTrue = "Not Empty";
        try {
            mBookPOJO.setName(nameFail);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setName(nameTrue);
        Assert.assertEquals(mBookPOJO.getName(), nameTrue);
    }


    @Test
    public void setUrl() {
        final BookPOJO mBookPOJO = new BookPOJO();
        final String failUrl1 = "aodhwaoud";
        final String failUrl2 = "";
        final String failUrl3 = "http://";
        final String trueUrl = "https://audioknigi.club/uploads/media/topic/2019/06/15/16/";
        final String trueUrl2 =
                "https://audioknigi.club/uploads/media/topic/2019/06/15/16/dhuiawdgaw.php";

        try {
            mBookPOJO.setUrl(failUrl1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrl(failUrl2);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }
        try {
            mBookPOJO.setUrl(failUrl3);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException thrown) {
            Assert.assertNotEquals("", thrown.getMessage());
        }

        mBookPOJO.setUrl(trueUrl);
        Assert.assertEquals(mBookPOJO.getUrl(), trueUrl);
        mBookPOJO.setUrl(trueUrl2);
        Assert.assertEquals(mBookPOJO.getUrl(), trueUrl2);
    }
}