package net.ustyugov.jtalk.listener;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import net.ustyugov.jtalk.Constants;
import net.ustyugov.jtalk.dialog.JuickMessageMenuDialog;

public class MyTextLinkClickListener implements TextLinkClickListener {
    private Context context;
    private String jid;

    public MyTextLinkClickListener(Context context, String jid) {
        if (jid == null) jid = "";
        this.context = context;
        this.jid = jid;
    }

    @Override
    public void onTextLinkClick(View textView, String clickedString) {
        if (clickedString.length() > 1) {
            if ((jid.equals(Constants.JUICK) || jid.equals(Constants.POINT))
                    && (clickedString.startsWith("@") || clickedString.startsWith("#"))) {
                new JuickMessageMenuDialog(context, clickedString).show();
            } else {
                Uri uri = Uri.parse(clickedString);
                if ((uri != null && uri.getScheme() != null)) {
                    String scheme = uri.getScheme().toLowerCase();
                    if (scheme.contains("http") || scheme.contains("xmpp")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(uri);
                        context.startActivity(intent);
                    }
                } else {
                    Intent intent = new Intent(Constants.PASTE_TEXT);
                    intent.putExtra("text", clickedString);
                    context.sendBroadcast(intent);
                }
            }
        }
    }
}
