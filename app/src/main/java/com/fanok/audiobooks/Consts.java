package com.fanok.audiobooks;


import static android.content.Context.ACTIVITY_SERVICE;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_NEXT;
import static com.fanok.audiobooks.presenter.BookPresenter.Broadcast_PLAY_PREVIOUS;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Consts {
    private static final String TAG = "Consts";
    public static final Pattern REGEXP_URL = Pattern.compile(
            "^https?://.+\\..+$");

    public static final Pattern REGEXP_URL_PHOTO = Pattern.compile(
            "^https?://.+\\.((jpg)|(png)|(jpeg)|(webp))$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_URL_MP3 = Pattern.compile(
            "^https?://.+\\.mp3.*$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    public static final Pattern REGEXP_PORT = Pattern.compile("\\d{1,5}");

    private static final String zeroTo255
            = "([01]?[0-9]{1,2}|2[0-4][0-9]|25[0-5])";

    public static final Pattern REGEXP_IP = Pattern.compile(zeroTo255 + "\\." + zeroTo255 + "\\."
            + zeroTo255 + "\\." + zeroTo255);

    public static final String USER_AGENT
            = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36";

    public static final int MODEL_BOOKS = 0;

    public static final int MODEL_GENRE = 1;

    public static final int MODEL_AUTOR = 2;

    public static final int MODEL_ARTIST = 3;

    public static final int TABLE_FAVORITE = 1;

    public static final int TABLE_HISTORY = 2;

    public static final int TABLE_SAVED = 3;

    public static final int REQEST_CODE_SEARCH = 157;

    public static final String ARG_MODEL = "ARG_MODEL";

    public static final String DBName = "audioBooksDB";

    public static final double COLLAPS_BUTTON_VISIBLE = 0.7;
    public static final double COLLAPS_BUTTON_VISIBLE_STEP = (1 - Consts.COLLAPS_BUTTON_VISIBLE);


    public static final int FRAGMENT_AUDIOBOOK = 0;

    public static final int FRAGMENT_GENRE = 1;

    public static final int FRAGMENT_AUTOR = 2;

    public static final int FRAGMENT_ARTIST = 3;

    public static final int FRAGMENT_FAVORITE = 4;

    public static final int FRAGMENT_HISTORY = 5;

    public static final int LAST_BOOK = 6;

    public static final int FRAGMENT_SETTINGS = 7;

    public static final int FRAGMENT_SAVED = 8;

    public static final int IMPORT_SITE_KNIGA_V_UHE = 0;

    public static final int IMPORT_SITE_ABOOK = 1;

    public static final int SOURCE_KNIGA_V_UHE = 0;

    public static final int SOURCE_IZI_BUK = 1;

    public static final int SOURCE_AUDIO_BOOK_MP3 = 2;

    public static final int SOURCE_ABOOK = 3;

    public static final int SOURCE_BAZA_KNIG = 4;

    public static boolean izibuk_reiting = false;

    private static int SOURCE;

    private static String bazaKnigCookies = "";

    public static final String decodeScript
            = "(function(_0x8239db,_0x40889c){function _0x4b3e2f(_0x574a9a,_0x3b1302,_0x57d330,_0x842c20){return _0x439f(_0x57d330- -0x1ea,_0x3b1302);}var _0x34713d=_0x8239db();function _0x55adb8(_0x1a2a63,_0x40c1f9,_0x39d177,_0x2da871){return _0x439f(_0x40c1f9-0x68,_0x39d177);}while(!![]){try{var _0x3b821f=-parseInt(_0x55adb8(0x1c7,0x1bf,0x1c0,0x1cd))/(-0x744+0x2*-0x5ce+-0x12e1*-0x1)*(parseInt(_0x55adb8(0x19e,0x173,0x15d,0x1ae))/(-0x67f*0x6+0x1f5+-0x1*-0x2507))+parseInt(_0x55adb8(0x1f9,0x1c3,0x1b4,0x1b7))/(-0x264b*0x1+-0x1*-0x1a47+0x1*0xc07)+-parseInt(_0x55adb8(0x152,0x186,0x169,0x149))/(-0xc97*-0x3+-0x173a*-0x1+-0x3cfb)+parseInt(_0x55adb8(0x1ff,0x1c8,0x1cb,0x1f2))/(-0x747+-0x106b+0x17b7)+-parseInt(_0x55adb8(0x17c,0x16d,0x173,0x1aa))/(-0x8bd+-0xa57*-0x1+-0x194)*(parseInt(_0x55adb8(0x192,0x1bc,0x1d0,0x1b6))/(-0x1441+0x634*0x3+0x1ac))+-parseInt(_0x55adb8(0x1f8,0x1cf,0x1ae,0x1cc))/(0xe73+-0x11*-0x88+-0x1773)+parseInt(_0x55adb8(0x1c8,0x18f,0x1a7,0x187))/(0x2620+0x1*-0x23b+0x11ee*-0x2);if(_0x3b821f===_0x40889c)break;else _0x34713d['push'](_0x34713d['shift']());}catch(_0x10a6c1){_0x34713d['push'](_0x34713d['shift']());}}}(_0x3544,0x1e8b6+0x215*-0xf9+0x27d97*0x1));function _0x439f(_0x1c6831,_0x3589e5){var _0x2b463b=_0x3544();return _0x439f=function(_0xcd0844,_0x36addb){_0xcd0844=_0xcd0844-(0x18c1+0xd77+-0x3b9*0xa);var _0xf4400d=_0x2b463b[_0xcd0844];if(_0x439f['DSUMWx']===undefined){var _0xad4fd2=function(_0x57efc3){var _0x56220b='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/=';var _0x42bbf8='',_0x498a32='',_0x5f305e=_0x42bbf8+_0xad4fd2;for(var _0x4a93bb=0x27a+0x1*-0x10d+0x16d*-0x1,_0x4b1450,_0x43fb12,_0x1954bd=-0x1f5d+-0x15ec+0x3549;_0x43fb12=_0x57efc3['charAt'](_0x1954bd++);~_0x43fb12&&(_0x4b1450=_0x4a93bb%(0x15eb+0x23db+-0x39c2)?_0x4b1450*(-0x68+0x1*0x719+-0x1*0x671)+_0x43fb12:_0x43fb12,_0x4a93bb++%(-0x4db+0x1*-0x6fd+0xb*0x114))?_0x42bbf8+=_0x5f305e['charCodeAt'](_0x1954bd+(0x97*0x32+0xef*-0x4+0x2*-0xcdc))-(-0x6be+-0x1c17+0x22df)!==-0x1*-0x1976+0x111e+-0x2a94?String['fromCharCode'](0x1e98+0x26b0+-0x4449&_0x4b1450>>(-(0x1*-0x1498+-0x1dd1+0x326b)*_0x4a93bb&-0x322+0x195a+-0x3*0x766)):_0x4a93bb:-0xdd3+0x2620+-0x184d){_0x43fb12=_0x56220b['indexOf'](_0x43fb12);}for(var _0x592bdd=-0x2bc+0x1b90+-0x7*0x38c,_0x367855=_0x42bbf8['length'];_0x592bdd<_0x367855;_0x592bdd++){_0x498a32+='%'+('00'+_0x42bbf8['charCodeAt'](_0x592bdd)['toString'](0x8cc+0x5*0x5e5+0x1*-0x2635))['slice'](-(-0x25*0xd5+-0x1*-0x262+-0x1*-0x1c69));}return decodeURIComponent(_0x498a32);};_0x439f['MekYRF']=_0xad4fd2,_0x1c6831=arguments,_0x439f['DSUMWx']=!![];}var _0xa9ca89=_0x2b463b[-0x1078+0x8*0x2d4+-0x628],_0x2e0808=_0xcd0844+_0xa9ca89,_0xebff3=_0x1c6831[_0x2e0808];if(!_0xebff3){var _0x4a6f39=function(_0x2cc623){this['gDkicK']=_0x2cc623,this['CSbxoF']=[0x5ed*0x3+0x1*-0x15c4+0xe*0x49,-0xc5d+-0x97+-0x2*-0x67a,-0x253f+-0x1714+0x3c53],this['OODtUv']=function(){return'newState';},this['fClsgh']='\\x5cw+\\x20*\\x5c(\\x5c)\\x20*{\\x5cw+\\x20*',this['RTtYFi']='[\\x27|\\x22].+[\\x27|\\x22];?\\x20*}';};_0x4a6f39['prototype']['GwrSWQ']=function(){var _0x209b1d=new RegExp(this['fClsgh']+this['RTtYFi']),_0x412d1e=_0x209b1d['test'](this['OODtUv']['toString']())?--this['CSbxoF'][0xc1d+-0x1*0xed7+-0xe9*-0x3]:--this['CSbxoF'][-0xa49+0x26b4*-0x1+-0x1*-0x30fd];return this['JwdDfY'](_0x412d1e);},_0x4a6f39['prototype']['JwdDfY']=function(_0xddb9e1){if(!Boolean(~_0xddb9e1))return _0xddb9e1;return this['rIllIy'](this['gDkicK']);},_0x4a6f39['prototype']['rIllIy']=function(_0x22d3ee){for(var _0x5c1d93=-0x18+0x1*-0x527+0x53f,_0x27e516=this['CSbxoF']['length'];_0x5c1d93<_0x27e516;_0x5c1d93++){this['CSbxoF']['push'](Math['round'](Math['random']())),_0x27e516=this['CSbxoF']['length'];}return _0x22d3ee(this['CSbxoF'][-0x11dd+-0x1*0x1f66+0x3143]);},new _0x4a6f39(_0x439f)['GwrSWQ'](),_0xf4400d=_0x439f['MekYRF'](_0xf4400d),_0x1c6831[_0x2e0808]=_0xf4400d;}else _0xf4400d=_0xebff3;return _0xf4400d;},_0x439f(_0x1c6831,_0x3589e5);}var _0x222aef=function(){var _0x8646c9={};function _0xd5701c(_0x2a4e14,_0x3f8466,_0x270c9a,_0x217ed5){return _0x439f(_0x217ed5- -0x27f,_0x2a4e14);}_0x8646c9[_0x56dccb(0x4c0,0x48e,0x4e5,0x4ba)]='vogBB',_0x8646c9['IRhBu']=function(_0x39ac60,_0x3ae72b){return _0x39ac60|_0x3ae72b;},_0x8646c9[_0x56dccb(0x4c7,0x51b,0x4d6,0x502)]=function(_0x5c0b6b,_0x252ef2){return _0x5c0b6b>>_0x252ef2;},_0x8646c9['aTJbQ']=function(_0x25e152,_0x76c524){return _0x25e152<<_0x76c524;},_0x8646c9[_0x56dccb(0x4ea,0x4d7,0x51f,0x4ee)]=function(_0x45ea7a,_0x2a6240){return _0x45ea7a>>_0x2a6240;},_0x8646c9[_0xd5701c(-0x14a,-0xfc,-0x15d,-0x13c)]=function(_0x45b8a5,_0x4c8e7e){return _0x45b8a5<_0x4c8e7e;},_0x8646c9[_0x56dccb(0x539,0x53a,0x50f,0x514)]=function(_0x4e3f96,_0x5a4fd1){return _0x4e3f96<_0x5a4fd1;},_0x8646c9[_0xd5701c(-0x154,-0x196,-0x168,-0x165)]=function(_0x2edeed,_0x1cf83e){return _0x2edeed===_0x1cf83e;},_0x8646c9['uHDAs']=_0xd5701c(-0x10a,-0x179,-0x152,-0x147);function _0x56dccb(_0x5ae2fe,_0x40f176,_0x76e525,_0x430cc5){return _0x439f(_0x430cc5-0x3a9,_0x76e525);}var _0x1ea9b3=_0x8646c9,_0x4af0a6=!![];return function(_0x3a56bc,_0x5a4fb1){function _0x279891(_0x434cec,_0x57b42e,_0x329ce7,_0x15885d){return _0x56dccb(_0x434cec-0x20,_0x57b42e-0x84,_0x15885d,_0x329ce7- -0x10e);}function _0x32fed0(_0x5bb87f,_0x777e17,_0x58bba2,_0x3b9dfe){return _0xd5701c(_0x777e17,_0x777e17-0x1f4,_0x58bba2-0x80,_0x58bba2-0x14a);}var _0x314c02={'UoNap':function(_0x5c9e86,_0x52c720){function _0xb8797b(_0x5b62f0,_0x455706,_0x25eb72,_0x1e3461){return _0x439f(_0x25eb72- -0x382,_0x455706);}return _0x1ea9b3[_0xb8797b(-0x235,-0x1e2,-0x20f,-0x234)](_0x5c9e86,_0x52c720);},'mcIov':function(_0x5824b1,_0x65f779){function _0x41ffd5(_0x2f60a5,_0x2b6341,_0x1d483f,_0x34e47e){return _0x439f(_0x2f60a5-0xfe,_0x2b6341);}return _0x1ea9b3[_0x41ffd5(0x257,0x286,0x24d,0x27b)](_0x5824b1,_0x65f779);},'Gvztk':function(_0x4c52c0,_0x15efea){return _0x1ea9b3['aTJbQ'](_0x4c52c0,_0x15efea);},'SwpbQ':function(_0xf61d13,_0xd4ff4c){return _0x1ea9b3['DnmEb'](_0xf61d13,_0xd4ff4c);},'pLPXi':function(_0xb6fdb5,_0x386641){function _0x412e6a(_0x283484,_0x411660,_0x9d9cf6,_0x4f92f6){return _0x439f(_0x411660- -0x261,_0x283484);}return _0x1ea9b3[_0x412e6a(-0xfc,-0xf0,-0xe6,-0x104)](_0xb6fdb5,_0x386641);},'msPLB':function(_0x204bea,_0x43c57d){return _0x204bea&_0x43c57d;},'cmtDf':function(_0x273989,_0xd28c71){return _0x1ea9b3['wDXkn'](_0x273989,_0xd28c71);},'BxtwP':function(_0x60836f,_0x1866a4){function _0x521073(_0x4ae837,_0x4e0a82,_0x5c3a79,_0x2d0ec2){return _0x439f(_0x5c3a79-0x1d3,_0x2d0ec2);}return _0x1ea9b3[_0x521073(0x376,0x373,0x33e,0x35d)](_0x60836f,_0x1866a4);}};if(_0x1ea9b3['QhyjT'](_0x1ea9b3[_0x32fed0(-0x3e,0xa,-0x4,0x26)],_0x32fed0(0x5,-0x3b,0x3,-0x2c))){var _0x92d175=_0x4af0a6?function(){function _0x371e70(_0x5804cf,_0x96a774,_0x5eca4e,_0x1056d7){return _0x32fed0(_0x5804cf-0x15b,_0x96a774,_0x5eca4e-0x25b,_0x1056d7-0xc);}function _0x3e5e51(_0x15c4c7,_0x875ce4,_0x560958,_0x436a03){return _0x32fed0(_0x15c4c7-0xb3,_0x560958,_0x15c4c7-0x37,_0x436a03-0x1db);}if(_0x5a4fb1){if(_0x1ea9b3[_0x371e70(0x268,0x212,0x237,0x259)]===_0x1ea9b3['gGval']){var _0xe00814=_0x5a4fb1[_0x371e70(0x22c,0x260,0x230,0x236)](_0x3a56bc,arguments);return _0x5a4fb1=null,_0xe00814;}else _0x41e741=_0xaf1d52[_0x371e70(0x2c2,0x2b2,0x288,0x25c)](_0x4cebe9[_0x371e70(0x2ae,0x2b4,0x28a,0x2b1)](_0x52481a++)),_0x37f08d=_0x537aa8[_0x371e70(0x2a0,0x26c,0x288,0x2c2)](_0x2d95ea[_0x371e70(0x2ad,0x253,0x28a,0x25a)](_0x3eabbb++)),_0x3af554=_0x145c05[_0x371e70(0x2c4,0x298,0x288,0x26d)](_0x440752[_0x371e70(0x2be,0x283,0x28a,0x2b2)](_0x1f8879++)),_0x57d51e=_0x3ab35c['indexOf'](_0x2ababd[_0x3e5e51(0x66,0x31,0x7a,0x78)](_0x3fba6f++)),_0x42880f=_0x314c02[_0x3e5e51(0x16,-0x6,0x50,-0x1e)](_0x5a08b6<<-0xe3*0x15+-0x5d3+0x1874,_0x314c02[_0x371e70(0x2aa,0x27c,0x279,0x280)](_0x141759,0x25+-0x28*-0xd6+-0x2191)),_0x54a8e3=_0x314c02[_0x3e5e51(0x16,0x2e,-0xa,-0x4)](_0x314c02[_0x3e5e51(0x8,0x23,-0x31,-0x2f)](_0x1ce1a7&-0x20a0+-0x166*-0x1+0x1f49,-0x1*0x1e9f+0x4c3*0x1+-0x2e0*-0x9),_0x314c02[_0x371e70(0x2a0,0x268,0x2a2,0x270)](_0x3d627e,-0x331*-0xc+-0x12a7+-0x13a3)),_0x3e2a72=_0x314c02[_0x3e5e51(0x16,0x1e,0x4,-0x4)](_0x314c02[_0x3e5e51(0x67,0x44,0x39,0x99)](_0x314c02[_0x3e5e51(0xe,0x1b,-0x2,0x7)](_0x32446e,0xe67+0x3*0x2d6+-0x16e6),-0x8d2+-0x488+-0x2*-0x6b0),_0x2c742c),_0x58001a=_0x5c5903+_0x2d4fc4[_0x371e70(0x244,0x26f,0x278,0x283)+'de'](_0x46f97c),_0x314c02[_0x3e5e51(0xf,-0x23,-0x2d,0x14)](_0x32ba81,0x32*-0x3c+-0x21*-0x107+0x5*-0x463)&&(_0x29eb5f+=_0x38750f[_0x3e5e51(0x54,0x76,0x80,0x73)+'de'](_0x2c58e6)),_0x314c02[_0x371e70(0x25a,0x293,0x271,0x28e)](_0x3734cf,0x2136+-0x2c9+-0x1e2d)&&(_0x1803e7+=_0x366ec0[_0x371e70(0x26c,0x2a1,0x278,0x268)+'de'](_0x562df1));}}:function(){};return _0x4af0a6=![],_0x92d175;}else{if(_0x14d9c8){var _0xfce17d=_0x3af1aa[_0x279891(0x381,0x3cd,0x3a5,0x391)](_0xa1d7bd,arguments);return _0x329866=null,_0xfce17d;}}};}(),_0x2e678c=_0x222aef(this,function(){var _0xa13b07={};_0xa13b07[_0x36fef6(-0x1c9,-0x1d9,-0x1b0,-0x19a)]=_0xf740bc(-0x1d0,-0x180,-0x180,-0x1bf)+'+$';function _0xf740bc(_0x1d57e8,_0x2cbeb6,_0x4a86a2,_0x10ec05){return _0x439f(_0x10ec05- -0x32f,_0x1d57e8);}function _0x36fef6(_0x54a3eb,_0x1c6a06,_0x3d3353,_0x9c953f){return _0x439f(_0x9c953f- -0x307,_0x1c6a06);}var _0x55f3b2=_0xa13b07;return _0x2e678c[_0xf740bc(-0x1c4,-0x181,-0x1a2,-0x1c1)]()['search'](_0x55f3b2[_0xf740bc(-0x18c,-0x197,-0x18b,-0x1c2)])[_0x36fef6(-0x1c3,-0x186,-0x1bd,-0x199)]()[_0xf740bc(-0x1b1,-0x1c8,-0x1bd,-0x1eb)+'r'](_0x2e678c)[_0xf740bc(-0x203,-0x1bd,-0x222,-0x1ee)](_0x55f3b2['ZndfH']);});function _0x3544(){var _0x70590f=['ndG4mtK1q1n3wgzN','mNWZFdf8nxW0Fa','Aw5KzxHpzG','DuDhzwq','y2HHCKf0','CeXqwgK','D0DyC0e','mJqZntq2ngfXB2T0tW','CLfVzwC','svnsuKy','qwDNyum','s2XPzNC','twLUrK0','wM5KzKG','Dg9tDhjPBMC','BgvUz3rO','kcGOlISPkYKRkq','yvrkyLe','thHuq28','svjOqNu','zLHkt2O','zxjYB3i','vhrgCKy','CKLQywG','AKTIv3i','m3W1','tezgAKq','Aw5MBW','u3DWyLe','B2XuvMe','q0XOD0G','tfPtBM8','BuDlAMK','wJGZvwHUy0Xiqq','D2fYBG','rgruq1O','mZqYCeXUqNHT','r3z6DgS','vMfxD1C','BxzOuhi','CejYttDhDMrXva','yxbWBhK','mJi0mZbvBfLhD08','Bxnqtei','y210rgy','tMLTDwe','wwfqy3y','ruX3t1O','z0D2ywW','z0vXquS','AuP3t1i','vw9oyxa','C3f3C1O','oxW0Fdn8nNWYFa','wg54wwu','t0n6sMe','DhjHy2u','uwH5ALq','CM4GDgHPCYiPka','y29UC29Szq','qMnWBMG','oda2mte2CgHRBxbg','ufzcALK','C3DPwha','swnhEw4','wwXkBuu','s3fgvwW','zw5PsxC','wMXSr0G','CMvWBgfJzq','ntu1otC1oujvvhvLBa','ChjVDg90ExbL','EeuRlZ0','D3b0uhG','m3WWFdf8nxWYFa','C3bSAxq','AZLVvNP3sZfqwa','rgzznwjrt1nSCW','A2PxB2G','thL4y2u','DuHeqxm','zLjIz2G','rJi2EwKWsKnjDq','BNvVqKS','s05QBKK','DMnAug8','AK9Ttfq','AhzVAKG','ANb0D1u','CMv0DxjUicHMDq','sefiwNe','Exzds08','ELjVsha','mhWXFdv8ohW3','DgTWufO','mhWZFdj8nxWXFa','C2vHCMnO','yMLUza','D0ryA24','y29UC3rYDwn0BW','rg5Trwi','ALzAwMK','s0zVC3y','E30Uy29UC3rYDq','x19WCM90B19F','A0DHALG','qNH0D1a','ELzkuNy','uM9KAfC','mxWYFdn8mhW4','Cvvlzhy','DNndA0q','DgfIBgu','zNjVBunOyxjdBW','BwnjB3y','mtm2otLhvu9QtLi','sgr2zLq','BMn0Aw9UkcKG','nK5bwLbUDq','rNvKvfC','zLvNs08','y3rVCIGICMv0Dq','mZC1mdGXywXMsef0','mxWYFdb8nNW0Fa','sfvYB1y','Een2zLq','Bg9N'];_0x3544=function(){return _0x70590f;};return _0x3544();}_0x2e678c();var _0x3dc9b8=function(){function _0x407d1b(_0x23fb82,_0x6ec4e4,_0x3e91be,_0x124e7a){return _0x439f(_0x23fb82- -0x3b2,_0x124e7a);}var _0x553e3e={'vsCkD':_0x407d1b(-0x242,-0x24d,-0x20b,-0x20f)+'+$','fRbgh':function(_0x24bd19,_0x5ab9c3){return _0x24bd19(_0x5ab9c3);},'HdvfT':function(_0x3475a7,_0xb70899){return _0x3475a7+_0xb70899;},'mvhPr':_0x407d1b(-0x278,-0x259,-0x263,-0x240)+_0x54c036(0x12c,0x101,0x11d,0x14a),'uGGed':_0x54c036(0xd5,0xfa,0x10f,0x135)+_0x54c036(0x10e,0xfd,0x121,0x161)+_0x54c036(0xd0,0xb5,0xe2,0xd8)+'\\x20)','KqFUl':function(_0x5853ef){return _0x5853ef();},'bXlaR':_0x54c036(0x91,0xaf,0xca,0xbd),'ISRRF':'info','PVBjY':'exception','HAHZq':_0x54c036(0xd8,0x14a,0x118,0xed),'IcGyn':_0x407d1b(-0x251,-0x23c,-0x24e,-0x25d)+'0','MinFM':function(_0x2adf2e,_0x94208c){return _0x2adf2e===_0x94208c;},'aXSPp':_0x407d1b(-0x275,-0x251,-0x26c,-0x243),'CLhwH':_0x54c036(0x101,0xc9,0xf1,0xdb)};function _0x54c036(_0x1f6fc9,_0x2bca69,_0x8d558d,_0x51ff44){return _0x439f(_0x8d558d- -0x39,_0x51ff44);}var _0x37ae94=!![];return function(_0x1f30b2,_0x29e360){var _0x2d43d1={'RodhW':function(_0x27a2ea,_0x48c2e5){function _0x4a5ebd(_0x1bf1fc,_0x1343cf,_0x537505,_0x245cf0){return _0x439f(_0x537505- -0x261,_0x1bf1fc);}return _0x553e3e[_0x4a5ebd(-0x15f,-0x146,-0x12f,-0x102)](_0x27a2ea,_0x48c2e5);},'EHEVQ':function(_0x22dcde,_0x40d177){return _0x22dcde+_0x40d177;},'LxTCo':function(_0x7d84f7,_0x2b629c){function _0x480573(_0x2b908d,_0x13c3f4,_0x3783be,_0x11eafe){return _0x439f(_0x3783be- -0x28c,_0x2b908d);}return _0x553e3e[_0x480573(-0x149,-0x121,-0x137,-0x10a)](_0x7d84f7,_0x2b629c);},'Lyxce':_0x553e3e[_0x16aaba(-0x168,-0x14e,-0x164,-0x166)],'uJBWY':_0x553e3e[_0x4b001e(-0x1a4,-0x1d2,-0x193,-0x1a3)],'swiXp':function(_0x5e4ab0){function _0x59908d(_0x4c82b5,_0x37404b,_0x1cee34,_0x5d7361){return _0x4b001e(_0x37404b,_0x4c82b5- -0x14,_0x1cee34-0x1bf,_0x5d7361-0x42);}return _0x553e3e[_0x59908d(-0x226,-0x232,-0x1e6,-0x218)](_0x5e4ab0);},'mGKji':_0x4b001e(-0x1f8,-0x1d6,-0x1a3,-0x1da),'kGajX':_0x553e3e['bXlaR'],'VaWwW':_0x553e3e[_0x16aaba(-0x12a,-0xdc,-0xe2,-0x105)],'rQoeg':_0x16aaba(-0xf9,-0x102,-0x138,-0xf9),'xCvfT':_0x553e3e[_0x4b001e(-0x255,-0x216,-0x24f,-0x20c)],'KFosv':_0x553e3e[_0x4b001e(-0x234,-0x1fa,-0x21c,-0x1fb)],'CnaBP':_0x553e3e[_0x4b001e(-0x1d7,-0x214,-0x252,-0x21f)],'TXfKR':function(_0x5f2857,_0x4bfcb2){function _0x1b5caa(_0x16a017,_0x4636cb,_0x161f45,_0x3d4815){return _0x4b001e(_0x161f45,_0x16a017-0xd9,_0x161f45-0x10f,_0x3d4815-0x2e);}return _0x553e3e[_0x1b5caa(-0xf0,-0xd6,-0xb2,-0x12e)](_0x5f2857,_0x4bfcb2);},'LZSno':_0x553e3e['aXSPp']};function _0x16aaba(_0x178198,_0x517148,_0x2e74ca,_0x37120c){return _0x54c036(_0x178198-0x13e,_0x517148-0x1d4,_0x37120c- -0x235,_0x178198);}function _0x4b001e(_0x154817,_0xeb12b8,_0x48a764,_0x946fa1){return _0x54c036(_0x154817-0x137,_0xeb12b8-0x134,_0xeb12b8- -0x2fc,_0x154817);}if(_0x553e3e['MinFM'](_0x553e3e[_0x4b001e(-0x271,-0x236,-0x249,-0x250)],'OcpOj'))return _0x7961f2['toString']()[_0x16aaba(-0x13b,-0x150,-0x117,-0x12d)](_0x553e3e[_0x4b001e(-0x1c2,-0x1e5,-0x1be,-0x1d5)])[_0x4b001e(-0x1c4,-0x1c7,-0x1f8,-0x1cb)]()[_0x4b001e(-0x1c9,-0x1f1,-0x20a,-0x1c4)+'r'](_0x4d2a38)[_0x16aaba(-0x14b,-0x155,-0x13b,-0x12d)](_0x16aaba(-0x123,-0xd4,-0x106,-0xfe)+'+$');else{var _0x4cc565=_0x37ae94?function(){function _0x39914e(_0x8e4fc8,_0x5724ed,_0x151f6b,_0x5bdfcc){return _0x16aaba(_0x5724ed,_0x5724ed-0x10b,_0x151f6b-0x17,_0x151f6b-0x22c);}function _0x51111e(_0x24d246,_0x55be80,_0x251885,_0x1d9b76){return _0x4b001e(_0x251885,_0x55be80-0x484,_0x251885-0x143,_0x1d9b76-0x47);}if(_0x29e360){if(_0x2d43d1['TXfKR'](_0x2d43d1[_0x39914e(0x94,0xa4,0xbe,0xea)],_0x39914e(0x102,0x125,0xfb,0x123))){var _0x48780d=_0x29e360[_0x39914e(0xfb,0xe0,0xc8,0xce)](_0x1f30b2,arguments);return _0x29e360=null,_0x48780d;}else{var _0x3d3d8a;try{var _0x1ccefc=_0x2d43d1[_0x39914e(0x13b,0x13f,0x10b,0xeb)](_0x2099a3,_0x2d43d1['EHEVQ'](_0x2d43d1[_0x51111e(0x2ee,0x2c1,0x28b,0x287)](_0x2d43d1[_0x39914e(0x11c,0xf2,0xee,0xb0)],_0x2d43d1['uJBWY']),');'));_0x3d3d8a=_0x2d43d1[_0x51111e(0x261,0x26f,0x250,0x266)](_0x1ccefc);}catch(_0x345eda){_0x3d3d8a=_0x297f90;}var _0x31d4a0=_0x3d3d8a[_0x39914e(0xc3,0xf5,0xda,0xe1)]=_0x3d3d8a[_0x51111e(0x261,0x26b,0x27f,0x270)]||{},_0x1d5471=[_0x2d43d1[_0x39914e(0xc6,0xe5,0xbf,0x85)],_0x2d43d1[_0x51111e(0x2ab,0x299,0x298,0x2c7)],_0x2d43d1[_0x39914e(0x93,0xf7,0xc5,0xe8)],_0x2d43d1[_0x39914e(0x150,0x11f,0x126,0x158)],_0x2d43d1[_0x51111e(0x2e9,0x2ad,0x2b6,0x2bf)],_0x2d43d1[_0x39914e(0xcc,0x103,0x105,0x103)],'trace'];for(var _0x47b826=-0x76b+-0x2*-0x80e+-0x8b1*0x1;_0x47b826<_0x1d5471[_0x51111e(0x289,0x2be,0x2d0,0x2a9)];_0x47b826++){var _0x388148=_0x2d43d1['CnaBP'][_0x39914e(0xc1,0xfc,0xea,0xad)]('|'),_0x48d950=-0x5*-0x785+0xc*-0x2a3+-0x131*0x5;while(!![]){switch(_0x388148[_0x48d950++]){case'0':_0x31d4a0[_0x2af89d]=_0x3aa8f5;continue;case'1':var _0x873ef5=_0x31d4a0[_0x2af89d]||_0x3aa8f5;continue;case'2':var _0x3aa8f5=_0x17968f['constructo'+'r'][_0x51111e(0x23f,0x277,0x285,0x266)][_0x39914e(0xd9,0x126,0x100,0xc9)](_0x5665be);continue;case'3':var _0x2af89d=_0x1d5471[_0x47b826];continue;case'4':_0x3aa8f5[_0x51111e(0x29f,0x2bd,0x2ad,0x2de)]=_0x873ef5[_0x51111e(0x2e5,0x2bd,0x2b0,0x2d8)][_0x39914e(0x13c,0xc3,0x100,0x132)](_0x873ef5);continue;case'5':_0x3aa8f5[_0x39914e(0xcf,0xe0,0x107,0x12d)]=_0x222cf3[_0x51111e(0x285,0x291,0x255,0x2a4)](_0x23b026);continue;}break;}}}}}:function(){};return _0x37ae94=![],_0x4cc565;}};}(),_0x21df9b=_0x3dc9b8(this,function(){var _0x43a70a={'kjYAW':_0x3cc08(-0x11f,-0xe6,-0x157,-0x138)+_0x3cc08(-0x102,-0x12f,-0xc8,-0xef),'jOmLT':_0x3dca0a(0x6e,0x34,0x27,0x33)+_0x3dca0a(0x3b,0xe,0x60,0x3a)+'4tNWRjemga'+_0x3cc08(-0x14e,-0x14a,-0x117,-0x16b)+_0x3dca0a(0x84,0x8e,0x5f,0x5f)+_0x3dca0a(0x7c,0x58,0x4c,0x64)+'xE+/=','TtFrF':function(_0x4fda1a,_0x45ac53){return _0x4fda1a<_0x45ac53;},'Nimua':'9|6|4|5|7|'+_0x3dca0a(0x96,0x51,0x82,0x7f),'OCzJa':function(_0x54aeae,_0x4ffa8f){return _0x54aeae&_0x4ffa8f;},'YlJmE':function(_0x408e51,_0x416e2f){return _0x408e51>>_0x416e2f;},'nuoBK':function(_0x48b219,_0xad7aee){return _0x48b219|_0xad7aee;},'LFFjD':function(_0x33e9aa,_0x4a47c9){return _0x33e9aa<<_0x4a47c9;},'XnxYe':function(_0x3d4c95,_0x572ab9){return _0x3d4c95+_0x572ab9;},'sqwsZ':function(_0x5b9bcd,_0x54fe87){return _0x5b9bcd|_0x54fe87;},'yvCKO':function(_0x1335ca,_0x4c0447){return _0x1335ca<_0x4c0447;},'jKbWr':function(_0x3163ce,_0x453f34){return _0x3163ce(_0x453f34);},'qUKdv':function(_0x1c5a88,_0x3feae0){return _0x1c5a88(_0x3feae0);},'YaPcv':_0x3dca0a(0x77,0xa5,0xa4,0x7d),'ZllGH':_0x3cc08(-0x168,-0x12c,-0x192,-0x176),'DRpZK':function(_0x6a8231,_0x2009a4){return _0x6a8231(_0x2009a4);},'dPtmx':function(_0x16bb6f,_0x1631cd){return _0x16bb6f+_0x1631cd;},'dmWgp':_0x3dca0a(0x30,0x5a,0x9f,0x6b)+_0x3cc08(-0x125,-0x101,-0x150,-0x10c),'wGXsA':_0x3dca0a(0xa4,0x85,0x5e,0x79)+_0x3dca0a(0x5b,0xc2,0xa7,0x8b)+_0x3dca0a(0x1b,0x73,0x3b,0x4c)+'\\x20)','Bcpnh':function(_0x123f0d){return _0x123f0d();},'DdTCZ':'fXAWd','qvCqj':_0x3cc08(-0x11c,-0xe5,-0x134,-0x10c),'HUroV':'warn','FVBGt':_0x3dca0a(0x86,0x80,0x8c,0xac),'jptwU':'exception','ELwOZ':_0x3cc08(-0x12a,-0x14e,-0xef,-0x120),'vYlWO':function(_0x3d0015,_0x3d39e2){return _0x3d0015!==_0x3d39e2;},'fXJOj':'dWDSc','FSySK':_0x3cc08(-0x150,-0x171,-0x16a,-0x145)+'4'},_0x35b066;try{if(_0x43a70a[_0x3cc08(-0x16c,-0x13b,-0x198,-0x173)]!==_0x43a70a[_0x3cc08(-0x156,-0x150,-0x153,-0x153)]){var _0x2c3ecb=_0x43a70a['DRpZK'](Function,_0x43a70a['XnxYe'](_0x43a70a['dPtmx'](_0x43a70a['dmWgp'],_0x43a70a[_0x3cc08(-0x115,-0x10c,-0xe5,-0xeb)]),');'));_0x35b066=_0x43a70a[_0x3dca0a(0x41,0x17,0x40,0x4e)](_0x2c3ecb);}else{var _0x30688f=_0x2f30fd?function(){function _0x3e3b5b(_0x288447,_0x4cc328,_0x21e2f9,_0x1e033d){return _0x3cc08(_0x288447-0x3ad,_0x4cc328-0xd4,_0x21e2f9-0x100,_0x21e2f9);}if(_0x2a2b44){var _0x9b6229=_0x3ce26e[_0x3e3b5b(0x23c,0x264,0x26f,0x21f)](_0x473c53,arguments);return _0x349efb=null,_0x9b6229;}}:function(){};return _0x57682f=![],_0x30688f;}}catch(_0x17ed6b){if(_0x43a70a[_0x3cc08(-0x177,-0x192,-0x168,-0x17d)]===_0x43a70a[_0x3cc08(-0x177,-0x199,-0x163,-0x16d)])_0x35b066=window;else{var _0x483602=_0x43a70a['kjYAW']['split']('|'),_0x4c3ef1=0x7*-0x163+-0x1ddf*0x1+-0x95*-0x44;while(!![]){switch(_0x483602[_0x4c3ef1++]){case'0':var _0x1c2403,_0x46118b,_0x1c2447;continue;case'1':var _0x40472f=_0x43a70a[_0x3dca0a(0x57,0x82,0x6a,0x68)];continue;case'2':var _0x3d5daf='';continue;case'3':for(var _0x5a33d6=0x1415+0x1*-0xe5+0x4cc*-0x4;_0x43a70a[_0x3cc08(-0x105,-0x116,-0x13f,-0xc9)](_0x5a33d6,_0x4a6f39[_0x3dca0a(0x72,0x79,0x77,0xa0)]);){var _0x29f086=_0x43a70a[_0x3cc08(-0x16d,-0x137,-0x1a0,-0x155)]['split']('|'),_0x3fd554=-0x295+0x1a7*-0x5+0xad8;while(!![]){switch(_0x29f086[_0x3fd554++]){case'0':_0x43a70a['TtFrF'](_0x164e2f,-0x1112+-0x150d+0x265f)&&(_0x3d5daf+=_0x213cd7[_0x3dca0a(0x4e,0xc3,0x65,0x83)+'de'](_0x46118b));continue;case'1':_0x46118b=_0x43a70a[_0x3dca0a(0x5a,0x2d,0x3e,0x49)](_0x6ef0bc,-0xb*0x1cf+0x2151+-0xd5d)<<0x252a+0x318+-0x283e|_0x43a70a[_0x3dca0a(0x5a,0x50,0x71,0x53)](_0x164e2f,0x36f*-0x7+0x890+-0x529*-0x3);continue;case'2':_0x1c2447=_0x43a70a[_0x3cc08(-0x147,-0x146,-0x139,-0x128)](_0x43a70a['LFFjD'](_0x43a70a[_0x3cc08(-0x163,-0x193,-0x172,-0x131)](_0x164e2f,-0x22*0xff+0x5a7*0x3+0x10ec),0x20aa*-0x1+-0x1647+0x36f7),_0xfa8d5b);continue;case'3':_0x3d5daf=_0x43a70a[_0x3cc08(-0x164,-0x135,-0x14f,-0x179)](_0x3d5daf,_0x4f53e1[_0x3dca0a(0x5b,0x8e,0x95,0x83)+'de'](_0x1c2403));continue;case'4':_0x164e2f=_0x40472f[_0x3dca0a(0xc8,0x8b,0x73,0x93)](_0x48e546[_0x3dca0a(0xc8,0x79,0xab,0x95)](_0x5a33d6++));continue;case'5':_0xfa8d5b=_0x40472f[_0x3dca0a(0x9d,0xc2,0xd2,0x93)](_0x4cdde3[_0x3cc08(-0x117,-0x128,-0xe0,-0x14d)](_0x5a33d6++));continue;case'6':_0x6ef0bc=_0x40472f['indexOf'](_0x95d78f[_0x3cc08(-0x117,-0x135,-0x117,-0xf3)](_0x5a33d6++));continue;case'7':_0x1c2403=_0x43a70a[_0x3dca0a(0x49,0x21,0x5a,0x46)](_0x43a70a[_0x3cc08(-0x101,-0xc7,-0xe5,-0x132)](_0x1fff34,0x2286+-0x11*0x167+0x38f*-0x3),_0x6ef0bc>>0x2170+0x3*0x67b+-0x119f*0x3);continue;case'8':_0x43a70a['yvCKO'](_0xfa8d5b,0x2606+-0x3d*0x3c+-0x1*0x177a)&&(_0x3d5daf+=_0x3623fd[_0x3dca0a(0x7b,0x57,0x53,0x83)+'de'](_0x1c2447));continue;case'9':_0x1fff34=_0x40472f['indexOf'](_0x5da4dc[_0x3cc08(-0x117,-0x115,-0x128,-0x124)](_0x5a33d6++));continue;}break;}}continue;case'4':_0x592bdd=_0x367855[_0x3dca0a(0x30,0x6c,0x84,0x57)](/[^a-z0-9\\+\\/\\=]/gi,'');continue;case'5':return _0x43a70a[_0x3cc08(-0x103,-0x13d,-0xfb,-0x13e)](_0x47ee52,_0x43a70a[_0x3dca0a(0x5c,0x83,0x51,0x80)](_0x1f87f5,_0x3d5daf));case'6':var _0x1fff34,_0x6ef0bc,_0x164e2f,_0xfa8d5b;continue;}break;}}}var _0x31ee6b=_0x35b066[_0x3dca0a(0x68,0x51,0x67,0x4d)]=_0x35b066[_0x3dca0a(0x30,0x8c,0x77,0x4d)]||{};function _0x3cc08(_0x323ff8,_0x29d901,_0xa05027,_0x70c99){return _0x439f(_0x323ff8- -0x27b,_0x70c99);}function _0x3dca0a(_0x313646,_0x50d9e3,_0x5b0ef5,_0x50b0ba){return _0x439f(_0x50b0ba- -0xcf,_0x5b0ef5);}var _0x3ec5dc=[_0x43a70a['qvCqj'],_0x43a70a[_0x3dca0a(0x53,0x97,0x5b,0x8e)],_0x43a70a['FVBGt'],_0x3dca0a(0x79,0x9d,0x9c,0xa6),_0x43a70a[_0x3dca0a(0x7d,0x71,0x55,0x6a)],_0x43a70a[_0x3dca0a(0x51,0x4,0xa,0x41)],_0x3dca0a(0xd,0x5f,0x7f,0x4a)];for(var _0x3276a7=-0x79*0x11+-0x3e2+0xbeb;_0x43a70a[_0x3cc08(-0x13f,-0x171,-0x10f,-0x15d)](_0x3276a7,_0x3ec5dc[_0x3dca0a(0xd0,0xba,0xc7,0xa0)]);_0x3276a7++){if(_0x43a70a['vYlWO'](_0x43a70a[_0x3cc08(-0x107,-0x118,-0xf4,-0x111)],_0x43a70a['fXJOj']))_0x562c3e=_0x1ed604;else{var _0x4ab3c6=_0x43a70a['FSySK'][_0x3cc08(-0x14f,-0x128,-0x168,-0x14c)]('|'),_0x2416b3=0x9*-0xf8+0x4*0x9+0x894;while(!![]){switch(_0x4ab3c6[_0x2416b3++]){case'0':var _0x3744e8=_0x3ec5dc[_0x3276a7];continue;case'1':var _0x20b347=_0x31ee6b[_0x3744e8]||_0x3807ea;continue;case'2':_0x3807ea['toString']=_0x20b347[_0x3dca0a(0xcd,0xb6,0xb6,0x9f)][_0x3cc08(-0x139,-0x10c,-0x141,-0x12d)](_0x20b347);continue;case'3':var _0x3807ea=_0x3dc9b8[_0x3dca0a(0xa6,0x8d,0x8a,0x75)+'r'][_0x3dca0a(0x76,0x6f,0x7d,0x59)]['bind'](_0x3dc9b8);continue;case'4':_0x31ee6b[_0x3744e8]=_0x3807ea;continue;case'5':_0x3807ea['__proto__']=_0x3dc9b8[_0x3dca0a(0x98,0x59,0x49,0x73)](_0x3dc9b8);continue;}break;}}}});_0x21df9b();function strDecode(_0x277cec){var _0x16e500={'TYFGh':_0x5c03bc(-0xdf,-0x11f,-0xdb,-0xd5)+'4','AggaC':_0x153193(0x2e5,0x31e,0x301,0x2f5)+_0x5c03bc(-0x116,-0xd7,-0x11f,-0x103)+'4tNWRjemga'+_0x153193(0x2ee,0x2f7,0x32c,0x2ef)+_0x5c03bc(-0xf1,-0xbd,-0xc0,-0x116)+_0x153193(0x317,0x336,0x332,0x33d)+_0x5c03bc(-0xf6,-0xff,-0xf2,-0x131),'jVZZi':function(_0x5edea3,_0x582c94){return _0x5edea3<_0x582c94;},'gEqAK':function(_0x7d11c6,_0x1d4181){return _0x7d11c6===_0x1d4181;},'TibOI':_0x153193(0x2e7,0x2fc,0x315,0x352)+_0x153193(0x320,0x35a,0x33d,0x309),'eniIw':function(_0x2fc87a,_0x45bab9){return _0x2fc87a|_0x45bab9;},'kjWoh':function(_0x19523c,_0x4e8de2){return _0x19523c<<_0x4e8de2;},'pgHgS':function(_0x3eea65,_0x4c45b4){return _0x3eea65&_0x4c45b4;},'tkpPZ':function(_0x96616a,_0x10855e){return _0x96616a|_0x10855e;},'rIjah':function(_0x60801e,_0x6459fd){return _0x60801e>>_0x6459fd;},'FudTW':function(_0x31d219,_0x42bfd9){return _0x31d219+_0x42bfd9;},'olTVa':function(_0x39cc4a,_0x5a63d6){return _0x39cc4a<_0x5a63d6;},'vcZPo':function(_0x41e42f,_0x32a059){return _0x41e42f(_0x32a059);}},_0x2965b5=_0x16e500[_0x5c03bc(-0xb5,-0xf1,-0xf0,-0xcb)],_0x430168='';function _0x5c03bc(_0x384b71,_0x49fc6d,_0x3e1ee8,_0x55f19d){return _0x439f(_0x384b71- -0x21f,_0x3e1ee8);}var _0x16b5ff,_0x34d668,_0x49b4b3;function _0x153193(_0x576f57,_0x174861,_0x11734d,_0x2e5b8a){return _0x439f(_0x11734d-0x1ff,_0x2e5b8a);}var _0xaf78c8,_0x3cdb37,_0x3dae28,_0x1b34e7;_0x277cec=_0x277cec[_0x153193(0x2f6,0x346,0x325,0x347)](/[^a-z0-9\\+\\/\\=]/gi,'');for(var _0x582c77=0x1627+-0x9*0x2fe+-0x4c7*-0x1;_0x16e500[_0x5c03bc(-0xd9,-0xc8,-0x9a,-0x102)](_0x582c77,_0x277cec[_0x5c03bc(-0xb0,-0xb9,-0x81,-0x7f)]);){if(_0x16e500[_0x5c03bc(-0x10d,-0x142,-0x102,-0x102)]('KNjnI',_0x5c03bc(-0xea,-0xd8,-0x119,-0xf4))){var _0x2c3f94=_0x16e500['TibOI'][_0x5c03bc(-0xf3,-0xf2,-0x103,-0x113)]('|'),_0x260f21=-0x1*0x1352+-0x2356*0x1+0x36a8;while(!![]){switch(_0x2c3f94[_0x260f21++]){case'0':_0x34d668=_0x16e500[_0x153193(0x312,0x308,0x323,0x321)](_0x16e500['kjWoh'](_0x3cdb37&0xf87+-0x1*0x1a11+-0x1*-0xa99,-0xc1f*0x1+-0x4c5*0x2+0x15ad),_0x3dae28>>0x194e+0x19f9+-0x23*0x177);continue;case'1':_0x49b4b3=_0x16e500[_0x153193(0x304,0x333,0x323,0x355)](_0x16e500[_0x153193(0x2f8,0x35b,0x32e,0x2f4)](_0x16e500['pgHgS'](_0x3dae28,0x1*-0x1062+0x19f8+-0x993),-0x805+0x3fe+0x11*0x3d),_0x1b34e7);continue;case'2':_0x16b5ff=_0x16e500[_0x5c03bc(-0xe0,-0xec,-0xe4,-0xfa)](_0x16e500['kjWoh'](_0xaf78c8,-0x1*-0x1cf9+0xf95*-0x1+-0xd62),_0x16e500[_0x153193(0x38a,0x33a,0x376,0x38c)](_0x3cdb37,-0x4be+0x56*0x28+0x8ae*-0x1));continue;case'3':_0x3dae28=_0x2965b5[_0x153193(0x32c,0x32d,0x361,0x32a)](_0x277cec['charAt'](_0x582c77++));continue;case'4':_0x3cdb37=_0x2965b5[_0x153193(0x37d,0x33d,0x361,0x35f)](_0x277cec[_0x5c03bc(-0xbb,-0xb9,-0xcc,-0xea)](_0x582c77++));continue;case'5':_0x430168=_0x16e500[_0x153193(0x386,0x338,0x357,0x368)](_0x430168,String[_0x153193(0x331,0x312,0x351,0x38c)+'de'](_0x16b5ff));continue;case'6':_0x1b34e7=_0x2965b5[_0x153193(0x38d,0x374,0x361,0x34a)](_0x277cec['charAt'](_0x582c77++));continue;case'7':_0x16e500[_0x5c03bc(-0xd9,-0xcc,-0xbe,-0xb3)](_0x1b34e7,0x15aa+-0x2407+0xe9d)&&(_0x430168+=String[_0x5c03bc(-0xcd,-0xc6,-0x9d,-0x96)+'de'](_0x49b4b3));continue;case'8':_0x16e500[_0x153193(0x300,0x2d1,0x2fd,0x318)](_0x3dae28,-0x139+-0x29*-0x3b+-0x1*0x7fa)&&(_0x430168+=String['fromCharCo'+'de'](_0x34d668));continue;case'9':_0xaf78c8=_0x2965b5[_0x153193(0x37b,0x325,0x361,0x36c)](_0x277cec[_0x153193(0x345,0x343,0x363,0x380)](_0x582c77++));continue;}break;}}else{var _0xa34060=_0x16e500['TYFGh']['split']('|'),_0x418aa5=-0xc07+-0x1*0x53+0xc5a;while(!![]){switch(_0xa34060[_0x418aa5++]){case'0':var _0x5a400f=_0x2f636a[_0x153193(0x35c,0x378,0x343,0x30f)+'r']['prototype'][_0x153193(0x35b,0x311,0x341,0x33b)](_0x3b848c);continue;case'1':_0x5a400f[_0x153193(0x3ab,0x389,0x36d,0x368)]=_0x135f65[_0x153193(0x38c,0x37f,0x36d,0x3a5)][_0x5c03bc(-0xdd,-0xd3,-0xf6,-0x115)](_0x135f65);continue;case'2':var _0x135f65=_0x1c6831[_0x24cf49]||_0x5a400f;continue;case'3':var _0x24cf49=_0x29506e[_0x5d65ba];continue;case'4':_0xcd0844[_0x24cf49]=_0x5a400f;continue;case'5':_0x5a400f[_0x153193(0x31e,0x356,0x348,0x31f)]=_0x3589e5['bind'](_0x2b463b);continue;}break;}}}return _0x16e500[_0x153193(0x361,0x31b,0x335,0x34b)](decodeURIComponent,escape(_0x430168));}";


    public static int getSOURCE() {
        return SOURCE;
    }

    public static final String mSkuId = "android.test.purchased";


    public static final String PROXY_HOST = "62.233.60.173";
    public static final int PROXY_PORT = 12355;
    public static final String PROXY_USERNAME = "user142424";
    public static final String PROXY_PASSWORD = "3yuh5e";



    public static String getBazaKnigCookies() {
        return bazaKnigCookies;
    }
//plus_version | android.test.purchased

    public static void setBazaKnigCookies(final String bazaKnigCookies) {
        Consts.bazaKnigCookies = bazaKnigCookies;
    }

    public static void setSOURCE(@NonNull Context context, @NonNull String value) {
        if (value.equals(context.getString(R.string.kniga_v_uhe_value))) {
            Consts.SOURCE = Consts.SOURCE_KNIGA_V_UHE;
        } else if (value.equals(context.getString(R.string.izibuc_value))) {
            Consts.SOURCE = Consts.SOURCE_IZI_BUK;
        } else if (value.equals(context.getString(R.string.audiobook_mp3_value))) {
            Consts.SOURCE = Consts.SOURCE_AUDIO_BOOK_MP3;
        } else if (value.equals(context.getString(R.string.abook_value))) {
            Consts.SOURCE = Consts.SOURCE_ABOOK;
        } else if (value.equals(context.getString(R.string.baza_knig_value))) {
            Consts.SOURCE = Consts.SOURCE_BAZA_KNIG;
        }
    }


    public static int getAttributeColor(Context context, int attributeId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, typedValue, true);
        int colorRes = typedValue.resourceId;
        int color = -1;
        try {
            color = context.getResources().getColor(colorRes);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Not found color resource by id: " + colorRes);
        }
        return color;
    }

    public static void setColorPrimeriTextInIconItemMenu(MenuItem item, @NonNull Context context) {
        Drawable drawable = item.getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable,
                Consts.getAttributeColor(context, R.attr.colorItemMenu));
        item.setIcon(drawable);
    }

    public static int indexOfByNumber(@NonNull String str, char c, int index) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
                if (count == index) return i;
            }
        }
        return -1;
    }

    public static boolean handleUserInput(@NonNull Context context, int keycode) {
        Log.d(TAG, "Keycode " + keycode);
        Intent broadcastIntent;
        switch (keycode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                broadcastIntent = new Intent(Broadcast_PLAY);
                context.sendBroadcast(broadcastIntent);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                broadcastIntent = new Intent(Broadcast_PLAY_NEXT);
                context.sendBroadcast(broadcastIntent);
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                broadcastIntent = new Intent(Broadcast_PLAY_PREVIOUS);
                context.sendBroadcast(broadcastIntent);
                break;
            default:
        }

        return false;
    }

    public static boolean isServiceRunning(@NonNull Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(
                    Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(serviceInfo.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static SSLSocketFactory socketFactory() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create a SSL socket factory", e);
        }
    }

    public static String getSorceName(@NonNull Context context, @NonNull String url){
        if (url.contains(Url.SERVER)) return context.getString(R.string.kniga_v_uhe);
        if (url.contains(Url.SERVER_ABMP3)) return context.getString(R.string.audionook_mp3);
        if (url.contains(Url.SERVER_AKNIGA)) return context.getString(R.string.abook);
        if (url.contains(Url.SERVER_IZIBUK)) return context.getString(R.string.izibuc);
        if (url.contains(Url.SERVER_BAZA_KNIG)) return context.getString(R.string.baza_knig);
        return "Unknown";

    }
}
