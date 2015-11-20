package fi.aalto.trafficsense.regularroutes.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGImageView;
import com.caverock.androidsvg.SVGParseException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import fi.aalto.trafficsense.regularroutes.R;


public class EnergyCertificateFragment extends Fragment {

    //private TextView textv;
    private SVGImageView svgImageView = null;
    private Context context;


    public EnergyCertificateFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        context = getActivity();
        System.out.println(context.toString());
        svgImageView = new SVGImageView(context);
        container.addView(svgImageView, ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_energy_certificate, container, false);
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        fetchCertificate();


    }

    @Override
    public void onDetach() {
        super.onDetach();
    }




    private void fetchCertificate() {
        try {
            //URL url = new URL("http://91.153.156.49:5000/grade_date/2015-09-14");
            URL url = new URL("http://91.156.99.21:5000/svg");
            DownloadDataTask downloader = new DownloadDataTask();
            downloader.execute(url);
        } catch (MalformedURLException e) {
            Toast toast = Toast.makeText(context, "URL was broken", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
    }

    private class DownloadDataTask extends AsyncTask<URL, Void, String> {
        protected String doInBackground(URL... urls) {
            String returnVal = null;
            if (urls.length != 1) {
                return "Certificate downloader attempted to get more or less than one URL";
            }


            HttpURLConnection urlConnection = null;
            try {
                urlConnection = (HttpURLConnection) urls[0].openConnection();
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
                if (s.hasNext()) {
                    returnVal = s.next();
                }
                else {
                    returnVal = "No data!";
                }
                in.close();

            }
            catch (IOException e) {
                return "error connecting to URL";
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            return returnVal;
        }

        protected void onPostExecute(String info) {
            SVG svgImage;
            try {
                svgImage = SVG.getFromString(info);
            } catch(SVGParseException e) {
                Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT);
                toast.show();
                return;
            }
            svgImageView.setSVG(svgImage);
        }
    }

}
