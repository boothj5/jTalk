package net.ustyugov.jtalk.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.sip.SipAudioCall;
import android.net.sip.SipManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import com.jtalk2.R;
import net.ustyugov.jtalk.Colors;
import net.ustyugov.jtalk.Constants;
import net.ustyugov.jtalk.listener.CallListener;
import net.ustyugov.jtalk.service.JTalkService;

public class CallActivity extends Activity implements View.OnClickListener {
    private TextView status;
    private Chronometer chrono;
    private SipAudioCall call;
    private BroadcastReceiver stateReceiver;
    private boolean speakerMode = false;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setTheme(Colors.isLight ? R.style.AppThemeLight : R.style.AppThemeDark);
        setContentView(R.layout.call_activity);

        String account = getIntent().getStringExtra("account");
        String jid = getIntent().getStringExtra("jid");
        boolean incoming = getIntent().getBooleanExtra("incoming", false);

        setTitle(jid);

        status = (TextView) findViewById(R.id.status);
        status.setVisibility(View.VISIBLE);
        chrono = (Chronometer) findViewById(R.id.chrono);
        chrono.setVisibility(View.GONE);

        try {
            SipManager manager = JTalkService.getInstance().getSipManager(account);
            if (manager != null) {
                if (incoming) {
                    call = JTalkService.getInstance().getIncomingCall();
                    call.answerCall(30);
                    call.startAudio();
                    call.setSpeakerMode(false);
                    if (call.isMuted()) call.toggleMute();
                    chrono.setBase(SystemClock.elapsedRealtime());
                    chrono.start();
                } else {
                    call = manager.makeAudioCall("sip:"+account, "sip:"+jid, null, 60);
                    call.setListener(new CallListener(this), true);
                }
            }
        } catch (Exception e) {
            status.setText(e.getLocalizedMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            call.endCall();
            call.close();
            JTalkService.getInstance().setIncomingCall(null);
        } catch (Exception ignored) { }
    }

    @Override
    public void onResume() {
        super.onResume();

        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String state = intent.getStringExtra("state");
                if (state.equals("established")) {
                    status.setVisibility(View.GONE);
                    chrono.setVisibility(View.VISIBLE);
                    chrono.setBase(SystemClock.elapsedRealtime());
                    chrono.start();
                } else {
                    chrono.stop();
                    chrono.setVisibility(View.GONE);
                    status.setVisibility(View.VISIBLE);
                    status.setText(state);
                }
            }
        };
        registerReceiver(stateReceiver, new IntentFilter(Constants.INCOMING_CALL));
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(stateReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.button10) {
            call.sendDtmf(10);
        } else if (view.getId() == R.id.button11) {
            call.sendDtmf(11);
        } else if (view.getId() == R.id.muteButton) {
            call.toggleMute();
            if (call.isMuted()) {
                ((Button) view).setText("Unmute");
            } else {
                ((Button) view).setText("Mute");
            }
        } else if (view.getId() == R.id.speakerButton) {
            speakerMode = !speakerMode;
            call.setSpeakerMode(speakerMode);
        } else if (view.getId() == R.id.endButton) {
            finish();
        } else {
            int code = Integer.parseInt(((Button) view).getText().toString());
            call.sendDtmf(code);
        }
    }
}
