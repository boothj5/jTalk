package net.ustyugov.jtalk.imgur;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import net.ustyugov.jtalk.Notify;
import net.ustyugov.jtalk.service.JTalkService;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ImgurUploadTask extends AsyncTask<Void, Void, String> {
    private static final String TAG = ImgurUploadTask.class.getSimpleName();
    private static final String UPLOAD_URL = "https://api.imgur.com/3/image";

    private Activity mActivity;
    private Uri mImageUri;  // local Uri to upload
    private String jid;
    private String account;
    private String text;
    private MultiUserChat muc;

    public ImgurUploadTask(Uri imageUri, Activity activity, String account, String jid, String text, MultiUserChat muc) {
        this.mImageUri = imageUri;
        this.mActivity = activity;
        this.account = account;
        this.jid = jid;
        this.muc = muc;
        this.text = text;
    }

    @Override
    protected String doInBackground(Void... params) {
        Log.e(TAG, "Start UploadTask");
        InputStream imageIn;
        try {
            imageIn = mActivity.getContentResolver().openInputStream(mImageUri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not open InputStream", e);
            Notify.imgurFileProgress(FileTransfer.Status.error, e.getLocalizedMessage());
            return null;
        }

        HttpURLConnection conn = null;
        InputStream responseIn = null;

        try {
            conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setDoOutput(true);

            ImgurAuthorization.getInstance(mActivity).addToHttpURLConnection(conn);

            OutputStream out = conn.getOutputStream();
            copy(imageIn, out);
            out.flush();
            out.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                responseIn = conn.getInputStream();
                StringBuilder sb = new StringBuilder();
                Scanner scanner = new Scanner(responseIn);
                while (scanner.hasNext()) {
                    sb.append(scanner.next());
                }
                return sb.toString();
            }
            else {
                Log.i(TAG, "responseCode=" + conn.getResponseCode());
                responseIn = conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                Scanner scanner = new Scanner(responseIn);
                while (scanner.hasNext()) {
                    sb.append(scanner.next());
                }
                Log.i(TAG, "error response: " + sb.toString());
                Notify.imgurFileProgress(FileTransfer.Status.error, sb.toString());
                return null;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error during POST", ex);
            Notify.imgurFileProgress(FileTransfer.Status.error, ex.getLocalizedMessage());
            return null;
        } finally {
            try {
                responseIn.close();
            } catch (Exception ignore) {}
            try {
                conn.disconnect();
            } catch (Exception ignore) {}
            try {
                imageIn.close();
            } catch (Exception ignore) {}
        }
    }

    @Override
    protected void onPreExecute() {
        Notify.imgurFileProgress(FileTransfer.Status.in_progress, mImageUri.getLastPathSegment());
    }

    @Override
    protected void onPostExecute(String result) {
        if (result == null || result.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(result);
            String type = root.getJSONObject("data").getString("type");
            String link = root.getJSONObject("data").getString("link");
            long size = root.getJSONObject("data").getLong("size");
            int w = root.getJSONObject("data").getInt("width");
            int h = root.getJSONObject("data").getInt("height");

            String message = type + " " + w+"x"+h + " ["+humanReadableByteCount(size, true)+"]: " + link;
            if (text != null && !text.isEmpty()) message += " :\n" + text;

            if (muc != null && muc.isJoined()) {
                try {
                    muc.sendMessage(message);
                } catch (Exception ignored) {}
            }
            else {
                JTalkService.getInstance().sendMessage(account, jid, message);
            }

            Notify.imgurCancel();
        } catch (Exception e) {
            Notify.imgurFileProgress(FileTransfer.Status.error, e.getLocalizedMessage());
        }
    }

    private static int copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}