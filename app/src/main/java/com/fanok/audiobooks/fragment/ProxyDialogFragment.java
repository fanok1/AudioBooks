package com.fanok.audiobooks.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.net.AppProxy;
import com.fanok.audiobooks.net.ProxyConfig;
import com.fanok.audiobooks.net.ProxyCandidate;
import com.fanok.audiobooks.net.ProxyFinder;

/**
 * The "Прокси" setting: off, the built-in proxy, or the automatic choice of a public proxy. A choice applies at
 * once; the automatic mode shows the search as it runs and offers a new search.
 */
public class ProxyDialogFragment extends DialogFragment implements ProxyFinder.Listener {

    public static final String TAG = "ProxyDialogFragment";
    /** Sent to the parent's fragment manager when the dialog closes. */
    public static final String RESULT_CLOSED = "proxy_dialog_closed";

    private ProxyFinder finder;
    private RadioGroup modes;
    private View autoPanel;
    private TextView status;
    private ProgressBar progress;
    private Button rescan;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        finder = ProxyFinder.get(context);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_proxy, null, false);
        modes = view.findViewById(R.id.proxyModes);
        autoPanel = view.findViewById(R.id.proxyAutoPanel);
        status = view.findViewById(R.id.proxyStatus);
        progress = view.findViewById(R.id.proxyProgress);
        rescan = view.findViewById(R.id.proxyRescan);

        modes.check(radioOf(AppProxy.mode()));
        modes.setOnCheckedChangeListener((group, checkedId) -> choose(checkedId));
        selectOnClick(view, R.id.proxyModeOffCaption, R.id.proxyModeOff);
        selectOnClick(view, R.id.proxyModeBuiltinCaption, R.id.proxyModeBuiltin);
        selectOnClick(view, R.id.proxyModeAutoCaption, R.id.proxyModeAuto);
        rescan.setOnClickListener(v -> {
            finder.rescan();
            render(finder.state());
        });
        render(finder.state());

        return builder.setTitle(R.string.proxy_title)
                .setView(view)
                .setPositiveButton(R.string.proxy_close, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        finder.addListener(this);
        render(finder.state());
    }

    @Override
    public void onStop() {
        finder.removeListener(this);
        super.onStop();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isAdded()) {
            getParentFragmentManager().setFragmentResult(RESULT_CLOSED, new Bundle());
        }
    }

    @Override
    public void onProxyState(@NonNull ProxyFinder.State state) {
        render(state);
    }

    private static void selectOnClick(@NonNull View root, int captionId, int radioId) {
        RadioButton radio = root.findViewById(radioId);
        root.findViewById(captionId).setOnClickListener(v -> radio.setChecked(true));
    }

    private void choose(int checkedId) {
        String mode = checkedId == R.id.proxyModeAuto ? AppProxy.MODE_AUTO
                : checkedId == R.id.proxyModeBuiltin ? AppProxy.MODE_BUILTIN : AppProxy.MODE_OFF;
        if (!mode.equals(AppProxy.mode())) {
            AppProxy.setMode(requireContext(), mode);
        }
        render(finder.state());
    }

    private static int radioOf(@NonNull String mode) {
        switch (mode) {
            case AppProxy.MODE_AUTO:
                return R.id.proxyModeAuto;
            case AppProxy.MODE_BUILTIN:
                return R.id.proxyModeBuiltin;
            default:
                return R.id.proxyModeOff;
        }
    }

    private void render(@NonNull ProxyFinder.State state) {
        if (status == null) {
            return;
        }
        boolean auto = AppProxy.MODE_AUTO.equals(AppProxy.mode());
        autoPanel.setVisibility(auto ? View.VISIBLE : View.GONE);
        if (!auto) {
            return;
        }
        boolean scanning = state.phase == ProxyFinder.Phase.SCANNING;
        progress.setVisibility(scanning ? View.VISIBLE : View.GONE);
        if (scanning) {
            progress.setIndeterminate(state.total == 0);
            if (state.total > 0) {
                progress.setMax(state.total);
                progress.setProgress(state.tested);
            }
        }
        rescan.setEnabled(!scanning);
        CharSequence text = statusText(status.getContext(), state);
        status.setText(text);
        status.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
    }

    /** The automatic mode's status line in the dialog; empty before the first search. */
    @NonNull
    static CharSequence statusText(@NonNull Context context, @NonNull ProxyFinder.State state) {
        ProxyCandidate current = state.current;
        if (state.phase == ProxyFinder.Phase.SCANNING) {
            return state.total == 0 ? context.getString(R.string.proxy_status_lists)
                    : context.getString(R.string.proxy_status_scanning, state.tested, state.total, state.found);
        }
        if (state.phase == ProxyFinder.Phase.NO_NETWORK) {
            return context.getString(R.string.proxy_status_no_network);
        }
        if (current != null) {
            long ms = Math.max(0, current.latencyMs());
            return state.spares > 0
                    ? context.getString(R.string.proxy_status_working_spares, current.label(), ms, state.spares)
                    : context.getString(R.string.proxy_status_working, current.label(), ms);
        }
        if (state.phase == ProxyFinder.Phase.FAILED || state.lastScanAt > 0) {
            return context.getString(R.string.proxy_status_not_found);
        }
        return "";
    }

    /** The settings entry's summary: the mode and, for the automatic mode, how the search stands. */
    @NonNull
    public static CharSequence summary(@NonNull Context context) {
        switch (AppProxy.mode()) {
            case AppProxy.MODE_BUILTIN:
                ProxyConfig own = AppProxy.custom();
                return context.getString(R.string.proxy_summary_builtin, own.host + ":" + own.port);
            case AppProxy.MODE_AUTO:
                ProxyFinder.State state = ProxyFinder.get(context).state();
                ProxyCandidate current = state.current;
                if (state.phase == ProxyFinder.Phase.SCANNING) {
                    return context.getString(R.string.proxy_summary_auto_scanning);
                }
                if (state.phase == ProxyFinder.Phase.NO_NETWORK) {
                    return context.getString(R.string.proxy_summary_auto_no_network);
                }
                if (current != null) {
                    return context.getString(R.string.proxy_summary_auto_working, current.label(),
                            Math.max(0, current.latencyMs()));
                }
                if (state.phase == ProxyFinder.Phase.FAILED || state.lastScanAt > 0) {
                    return context.getString(R.string.proxy_summary_auto_not_found);
                }
                return context.getString(R.string.proxy_summary_auto);
            default:
                return context.getString(R.string.proxy_summary_off);
        }
    }
}
