package com.agnes.video;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private WebView wv;
    private String pendingWhich = null;
    private static final int REQ_PICK = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " AgnesApp/2.0");
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
        wv.addJavascriptInterface(new Bridge(this), "Android");
        wv.loadUrl("file:///android_asset/index.html");
        setContentView(wv);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            wv.evaluateJavascript(
                "(function(){return (window.agnesBack && agnesBack()) ? 1 : 0})()",
                new android.webkit.ValueCallback<String>() {
                    public void onReceiveValue(String v) {
                        boolean handled = (v != null && v.indexOf('1') >= 0);
                        if (handled) return;
                        if (wv.canGoBack()) wv.goBack();
                        else finish();
                    }
                });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toastMsg(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void startPicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, "选择参考图"), REQ_PICK);
        } catch (Exception e) {
            pendingWhich = null;
            toastMsg("这台机器没有图片选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK) {
            final String which = pendingWhich;
            pendingWhich = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && which != null) {
                try {
                    Uri uri = data.getData();
                    BitmapFactory.Options probe = new BitmapFactory.Options();
                    probe.inJustDecodeBounds = true;
                    InputStream in0 = getContentResolver().openInputStream(uri);
                    BitmapFactory.decodeStream(in0, null, probe);
                    in0.close();

                    int sample = 1;
                    int big = Math.max(probe.outWidth, probe.outHeight);
                    while (big / sample > 2560) sample *= 2;

                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    InputStream in1 = getContentResolver().openInputStream(uri);
                    Bitmap bmp = BitmapFactory.decodeStream(in1, null, o2);
                    in1.close();
                    if (bmp == null) {
                        toastMsg("图片解码失败");
                        return;
                    }

                    int w = bmp.getWidth();
                    int h = bmp.getHeight();
                    int max = 1280;
                    if (w > max || h > max) {
                        float scale = (w >= h) ? (float) max / w : (float) max / h;
                        int nw = Math.max(1, Math.round(w * scale));
                        int nh = Math.max(1, Math.round(h * scale));
                        Bitmap small = Bitmap.createScaledBitmap(bmp, nw, nh, true);
                        if (small != bmp) bmp.recycle();
                        bmp = small;
                        w = nw;
                        h = nh;
                    }

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos);
                    bmp.recycle();
                    String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    bos.close();

                    final String js = "onImagePicked('" + which + "','data:image/jpeg;base64," + b64 + "'," + w + "," + h + ")";
                    wv.post(new Runnable() {
                        public void run() {
                            wv.evaluateJavascript(js, null);
                        }
                    });
                } catch (Exception e) {
                    toastMsg("读图失败：" + e.getMessage());
                } catch (OutOfMemoryError e) {
                    toastMsg("图片太大，换一张小点的");
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public class Bridge {
        private final Context ctx;

        Bridge(Context c) {
            ctx = c;
        }

        @JavascriptInterface
        public void pickImage(String which) {
            pendingWhich = which;
            runOnUiThread(new Runnable() {
                public void run() {
                    startPicker();
                }
            });
        }

        @JavascriptInterface
        public void saveVideo(String url, String name) {
            saveFile(url, name, "video/mp4");
        }

        @JavascriptInterface
        public void saveFile(String url, String name, String mime) {
            try {
                String file = name == null || name.trim().isEmpty() ? "agnes" : name.trim();
                file = file.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (file.length() > 40) file = file.substring(0, 40);
                String ext = "mp4";
                if (mime != null && mime.contains("image")) ext = "png";
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setTitle(file + "." + ext);
                r.setMimeType(mime == null ? "video/mp4" : mime);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file + "." + ext);
                DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(r);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(ctx, "已开始下载到 Download 目录", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                final String m = e.toString();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(ctx, "下载失败：" + m, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }

        @JavascriptInterface
        public void openUrl(String u) {
            final String url = u;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(ctx, "打不开这个链接", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void toast(String msg) {
            final String m = msg;
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
