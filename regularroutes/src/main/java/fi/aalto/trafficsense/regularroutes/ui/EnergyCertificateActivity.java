package fi.aalto.trafficsense.regularroutes.ui;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.caverock.androidsvg.PreserveAspectRatio;
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
import fi.aalto.trafficsense.regularroutes.RegularRoutesApplication;
import fi.aalto.trafficsense.regularroutes.RegularRoutesConfig;
import fi.aalto.trafficsense.regularroutes.backend.BackendStorage;
import fi.aalto.trafficsense.regularroutes.backend.rest.RestApi;
import fi.aalto.trafficsense.regularroutes.util.HandlerExecutor;
import retrofit.RestAdapter;

public class EnergyCertificateActivity extends Activity {

    private RelativeLayout container;
    private SVGImageView svgImageView;
    private BackendStorage mStorage;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy_certificate);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        container = (RelativeLayout) findViewById(R.id.energy_certificate);
        svgImageView = new SVGImageView(this);
        container.addView(svgImageView, ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);

        mStorage = BackendStorage.create(this);

    }

    @Override
    protected void onStart() {
        super.onStart();
        fetchCertificate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchCertificate();
    }

    private void fetchCertificate() {
        try {
            String sessionToken = mStorage.readSessionToken().get();
            final RegularRoutesConfig config = ((RegularRoutesApplication) this.getApplication()).getConfig();
            URL url = new URL(config.server.toString() + "/svg/" + sessionToken);
            DownloadDataTask downloader = new DownloadDataTask();
            downloader.execute(url);
        } catch (MalformedURLException e) {
            Context context = getApplicationContext();
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
                Context context = getApplicationContext();
                Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT);
                toast.show();
                return;
            }
            svgImageView.setSVG(svgImage);
        }
    }
}
