package net.ustyugov.jtalk.listener;

import android.content.Context;
import android.content.Intent;
import android.net.sip.SipAudioCall;
import net.ustyugov.jtalk.Constants;
import net.ustyugov.jtalk.Notify;

public class CallListener extends SipAudioCall.Listener {
    private Context context;

    public CallListener(Context context) {
        this.context = context;
    }

    @Override
    public void onCalling(SipAudioCall call) {
        Intent intent = new Intent(Constants.INCOMING_CALL);
        intent.putExtra("state", "Calling...");
        context.sendBroadcast(intent);
    }
    @Override
    public void onCallEstablished(SipAudioCall call) {
        call.startAudio();
        call.setSpeakerMode(false);
        if (call.isMuted()) call.toggleMute();

        Intent intent = new Intent(Constants.INCOMING_CALL);
        intent.putExtra("state", "established");
        context.sendBroadcast(intent);
    }
    @Override
    public void onCallEnded(SipAudioCall call) {
        call.close();
        Intent intent = new Intent(Constants.INCOMING_CALL);
        intent.putExtra("state", "Ended");
        context.sendBroadcast(intent);
        Notify.cancelCallNotify(context);
    }
    @Override
    public void onCallBusy(SipAudioCall call) {
        Intent intent = new Intent(Constants.INCOMING_CALL);
        intent.putExtra("state", "Busy");
        context.sendBroadcast(intent);
        Notify.cancelCallNotify(context);
    }

    @Override
    public void onError(SipAudioCall call, final int errorCode, final String errorMessage) {
        call.close();
        Intent intent = new Intent(Constants.INCOMING_CALL);
        intent.putExtra("state", "[" + errorCode + "] " + errorMessage);
        context.sendBroadcast(intent);
        Notify.cancelCallNotify(context);
    }
}
