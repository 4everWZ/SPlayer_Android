package top.imsyy.splayer;

import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;
import top.imsyy.splayer.media.AndroidMediaBridgePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(AndroidMediaBridgePlugin.class);
        super.onCreate(savedInstanceState);
        initSafeAreaInsetsSync();
    }

    @Override
    public void onResume() {
        super.onResume();
        requestSafeAreaInsetsSync();
        getWindow().getDecorView().postDelayed(this::requestSafeAreaInsetsSync, 300);
        getWindow().getDecorView().postDelayed(this::requestSafeAreaInsetsSync, 1000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            requestSafeAreaInsetsSync();
        }
    }

    private void initSafeAreaInsetsSync() {
        final View decorView = getWindow().getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (view, windowInsets) -> {
            syncSafeAreaInsets(windowInsets);
            return windowInsets;
        });
        decorView.post(this::requestSafeAreaInsetsSync);
    }

    private void requestSafeAreaInsetsSync() {
        final View decorView = getWindow().getDecorView();
        ViewCompat.requestApplyInsets(decorView);
        final WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(decorView);
        if (windowInsets != null) {
            syncSafeAreaInsets(windowInsets);
        }
    }

    private void syncSafeAreaInsets(WindowInsetsCompat windowInsets) {
        final Insets insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
        );
        applySafeAreaInsets(insets.top, insets.right, insets.bottom, insets.left);
    }

    private void applySafeAreaInsets(int top, int right, int bottom, int left) {
        if (bridge == null) {
            return;
        }
        final WebView webView = bridge.getWebView();
        if (webView == null) {
            return;
        }
        final String script =
                "(function(){" +
                        "const root=document.documentElement;" +
                        "if(!root){return;}" +
                        "root.style.setProperty('--safe-area-inset-top','" + top + "px');" +
                        "root.style.setProperty('--safe-area-inset-right','" + right + "px');" +
                        "root.style.setProperty('--safe-area-inset-bottom','" + bottom + "px');" +
                        "root.style.setProperty('--safe-area-inset-left','" + left + "px');" +
                        "})();";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
