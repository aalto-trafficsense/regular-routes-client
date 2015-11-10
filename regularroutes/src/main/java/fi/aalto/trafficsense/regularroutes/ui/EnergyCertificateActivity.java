package fi.aalto.trafficsense.regularroutes.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import fi.aalto.trafficsense.regularroutes.R;

public class EnergyCertificateActivity extends Activity {

    private TextView textv;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy_certificate);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        textv = (TextView) findViewById(R.id.textView2);
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
            URL url = new URL("http://91.153.156.49:5000/grade_date/2015-09-14");
            DownloadDataTask downloader = new DownloadDataTask();
            downloader.execute(url);
        } catch (MalformedURLException e) {
            textv.setText("URL was broken");
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
            textv.setText("GOT INFO: " + info);
        }
    }
}
