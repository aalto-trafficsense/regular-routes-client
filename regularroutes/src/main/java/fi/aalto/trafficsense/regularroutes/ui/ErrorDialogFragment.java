package fi.aalto.trafficsense.regularroutes.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;


public class ErrorDialogFragment extends DialogFragment {
    public static final String KEY_MESSAGE = "MESSAGE";



    public ErrorDialogFragment() {
        // Required empty public constructor
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final String msg = getArguments().getString(KEY_MESSAGE, "");
        AlertDialog dlg  = new AlertDialog
                .Builder(getActivity())
                .setMessage(msg)
                .setTitle("Error")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // close
                        dismiss();
                    }
                })
                .create();

        return dlg;
    }


    /* Static Methods */
    public static ErrorDialogFragment createInstance(String errorMsg) {
        ErrorDialogFragment frm = new ErrorDialogFragment();
        Bundle args = new Bundle();
        args.putString(KEY_MESSAGE, errorMsg);
        frm.setArguments(args);
        return frm;
    }

}
