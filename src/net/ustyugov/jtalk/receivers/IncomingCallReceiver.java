package net.ustyugov.jtalk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.sip.SipAudioCall;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.util.Log;
import net.ustyugov.jtalk.Notify;
import net.ustyugov.jtalk.service.JTalkService;

public class IncomingCallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        String account = intent.getStringExtra("account");
        if (account == null) return;
        try {
            JTalkService service = JTalkService.getInstance();
            SipManager sipManager = service.getSipManager(account);
            if (sipManager != null) {
                SipAudioCall incomingCall = sipManager.takeAudioCall(intent, null);
                if (incomingCall != null) {
                    service.setIncomingCall(incomingCall);
                    Notify.callNotify(context, account, incomingCall.getPeerProfile().getDisplayName());
                }
            }
        } catch (SipException e) {
            Log.e("IncomingListener", e.getLocalizedMessage());
        }
    }
}
