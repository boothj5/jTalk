package net.ustyugov.jtalk;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import net.ustyugov.jtalk.view.MyTextView;

import java.io.*;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pictures {
    static Pattern linkPattern = Pattern.compile("https?://[a-z0-9\\-\\.]+[a-z]{2,}/[a-z0-9\\W]*[.png|.jpg|.jpeg|.gif]{1}[^\\s\\n]*", Pattern.CASE_INSENSITIVE);

    public static void loadPicture(final Activity activity, final String jid, final SpannableStringBuilder ssb, final MyTextView tv) {
        new Thread() {
            @Override
            public void run() {
                Matcher m = linkPattern.matcher(ssb);
                while (m.find())
                {
                    try {
                        URL url = new URL(ssb.subSequence(m.start(), m.end()).toString());
                        String file = url.getPath().substring(url.getPath().lastIndexOf("/")+1, url.getPath().length());
                        String fname = Constants.PATH + file;

                        if (!new File(fname).exists()) {
                            BufferedInputStream in = new BufferedInputStream(url.openStream());
                            FileOutputStream fout = new FileOutputStream(fname);

                            final byte data[] = new byte[1024];
                            int count;
                            while ((count = in.read(data, 0, 1024)) != -1) {
                                fout.write(data, 0, count);
                            }
                            in.close();
                            fout.close();

                            activity.sendBroadcast(new Intent(Constants.PRESENCE_CHANGED).putExtra("jid", jid));
                        } else {
                            DisplayMetrics metrics = new DisplayMetrics();
                            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                            float scaleWidth = metrics.scaledDensity;
                            float scaleHeight = metrics.scaledDensity;

                            Matrix matrix = new Matrix();
                            matrix.postScale(scaleWidth, scaleHeight);

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                            int sidebar = prefs.getInt("SideBarSize", 100);

                            Bitmap bitmap = BitmapFactory.decodeFile(fname);
                            bitmap.setDensity(metrics.densityDpi);
                            int maxWidth = metrics.widthPixels - sidebar;
                            int width = bitmap.getWidth();
                            if (width > maxWidth)  {
                                double k = (double)width/(double)maxWidth;
                                int h = (int) (bitmap.getHeight()/k);
                                bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, h, true);
                            }

                            ssb.insert(m.end(), "\np\n");
                            ssb.setSpan(new ImageSpan(activity, bitmap, ImageSpan.ALIGN_BASELINE), m.end()+1, m.end()+2, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    tv.setText(ssb);
                                }
                            });
                        }
                    } catch (Exception ignored) { }
                }
            }
        }.start();
    }
}
