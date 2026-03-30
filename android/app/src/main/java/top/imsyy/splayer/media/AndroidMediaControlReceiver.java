package top.imsyy.splayer.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AndroidMediaControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        AndroidMediaBridgePlugin.dispatchAction(intent.getAction());
    }
}
