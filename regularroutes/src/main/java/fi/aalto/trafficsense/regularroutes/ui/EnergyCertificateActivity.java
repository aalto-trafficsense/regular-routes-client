package fi.aalto.trafficsense.regularroutes.ui;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.view.Display;
import android.widget.LinearLayout;
import android.widget.TextView;
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

public class EnergyCertificateActivity extends Activity {

    //private TextView textv;
    private SVG svgImage;
    private LinearLayout container;
    SVGImageView svgImageView;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_energy_certificate);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        container = (LinearLayout) findViewById(R.id.energy_certificate);
        //textv = (TextView) findViewById(R.id.textView2);
        svgImageView = new SVGImageView(this);
        container.addView(svgImageView, ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.MATCH_PARENT);

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
            //URL url = new URL("http://91.153.156.49:5000/grade_date/2015-09-14");
            URL url = new URL("http://91.153.156.49:5000/svg");
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
            try {
                svgImage = SVG.getFromString(info);
            } catch(SVGParseException e) {
                Context context = getApplicationContext();
                Toast toast = Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT);
                toast.show();
                return;
            }
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            //svgImage.setDocumentWidth(size.x);
            //svgImage.setDocumentHeight(size.y);
            //svgImage.setDocumentViewBox(0,0,size.x,size.y);
            svgImageView.setSVG(svgImage);
            //textv.setText("Size: " + size.x + ", " + size.y);
        }
    }
}
