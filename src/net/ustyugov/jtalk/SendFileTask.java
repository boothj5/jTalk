package net.ustyugov.jtalk;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import net.ustyugov.jtalk.Notify;
import net.ustyugov.jtalk.service.JTalkService;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;

import java.io.File;

public class SendFileTask extends AsyncTask<Void, Void, String> {
    private JTalkService service;
    private String account;
    private String jid;
    private String text;
    private Uri uri;
    private String path;
    private Activity activity;

    public SendFileTask(Activity activity, String account, String jid, String text, Uri uri) {
        this.account = account;
        this.jid = jid;
        this.text = text;
        this.uri = uri;
        this.activity = activity;
        this.service = JTalkService.getInstance();
    }

    @Override
    protected void onPreExecute() {
        String scheme = uri.getScheme();
        if (scheme.equals("file")) {
            path = uri.getPath();
        } else if (scheme.equals("content")) {
            try {
                String[] proj = { MediaStore.Files.FileColumns.DATA };
                Cursor cursor = activity.managedQuery(uri, proj, null, null, null);
                if (cursor != null && cursor.getCount() != 0) {
                    int columnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                    cursor.moveToFirst();
                    path = cursor.getString(columnIndex);
                }
            } catch(Exception e) { path = null; }
        }
    }

    @Override
    protected String doInBackground(Void... voids) {
        if (path == null) return "File not found";
        File file = new File(path);
        if (file.exists()) {
            String name = file.getName();
            try {
                FileTransferManager ftm = service.getFileTransferManager(account);
                if (ftm == null) return "FileTransferManager not initialized";
                OutgoingFileTransfer out = ftm.createOutgoingFileTransfer(jid);
                out.sendFile(file, text);

                FileTransfer.Status lastStatus = FileTransfer.Status.initial;
                while (!out.isDone()) {
                    FileTransfer.Status status = out.getStatus();
                    if (status != lastStatus) {
                        Notify.fileProgress(name, status);
                        lastStatus = status;
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) { }
                }
                Notify.fileProgress(name, out.getStatus());
            } catch (Exception e) {
                Notify.fileProgress(name, FileTransfer.Status.error);
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(String result) {
        if (result == null) return;
        Notify.imgurFileProgress(FileTransfer.Status.error, result);
    }
}
