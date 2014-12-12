package com.jorajipo.aydoctor.aydoctor;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by jorajipo on 08/12/2014.
 */
public class DirectoryFragment extends Fragment{

    private ArrayAdapter<String> mDirectoryAdapter;


    public DirectoryFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.directoryfragment,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            FetchDoctorsTask doctorsTask = new FetchDoctorsTask();

            doctorsTask.execute(""); //TODO: VERIFICAR PARAMETRO
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Create some dummy data for the ListView.  Here's a sample weekly forecast
        String[] data = {
                "Alfredo Granados - Medicina geeral - Leon",
                "Ricardo Cruz - Ortopedia - Leon",
                "Armando Flores - Urologia - Leon",
                "Martin Sandoval - Medicina General - Leon",
                "Ricardo Leal - Oncolgia - Leon",
                "Leopoldo Lopez - Psiquiatria - DF",
                "Alejandro Granados - Oftalmologia - Leon"
        };
        List<String> directoryList = new ArrayList<String>(Arrays.asList(data));

        // Now that we have some dummy data, create an ArrayAdapter.
        // The ArrayAdapter will take data from a source (like our dummy directory) and
        // use it to populate the ListView it's attached to.
        mDirectoryAdapter =
                new ArrayAdapter<String>(
                        getActivity(), // The current context (this activity)
                        R.layout.list_item_directoy, // The name of the layout ID.
                        R.id.list_item_directory_textview, // The ID of the textview to populate.
                        directoryList);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        ListView listView = (ListView) rootView.findViewById(R.id.listview_diectory);
        listView.setAdapter(mDirectoryAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                String directory = mDirectoryAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, directory);
                        startActivity(intent);

            }
        } );

        return rootView;
    }

    public class FetchDoctorsTask extends AsyncTask<String,Void,String[]> {

        private final String LOG_TAG = FetchDoctorsTask.class.getSimpleName();


        @Override
        protected String[] doInBackground(String... params) {
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String directoryJsonStr = null;

            String format = "json";
            String city = "";
            String spec = "";
            int numResults = 10;

            try {
                // Construct the URL for the AyDoctor query

                final String Directory_BASE_URL = "http://aydoctor.com/getdoctorsws.php?";
                final String FORMAT_PARAM = "format";
                final String CITY_PARAM = "city";
                final String SPECIALTY_PARAM = "spec";

                Uri builtUri = Uri.parse(Directory_BASE_URL).buildUpon()
                        .appendQueryParameter(CITY_PARAM,params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(SPECIALTY_PARAM,spec)
                        .build();

                URL url = new URL(builtUri.toString());
                Log.v(LOG_TAG, "Built URL: " + builtUri.toString());



                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                directoryJsonStr = buffer.toString();

                Log.v(LOG_TAG, "Directory JSON String: " + directoryJsonStr );

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attempting
                // to parse it.
                return null;
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getDoctorsDataFromJson(directoryJsonStr, numResults );
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {
                mDirectoryAdapter.clear();
                for (String  doctorDirectoryString : result) {
                    mDirectoryAdapter.add(doctorDirectoryString);
                }
            }
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getDoctorsDataFromJson(String directoryJsonStr, int numDocs)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "doctors";
            final String OWM_DOCTOR = "doctor";

            final String OWM_NAME = "nombre";
            final String OWM_SURNAME = "apellido";
            final String OWM_PHOTO =  "foto";
            final String OWM_DESCRIPTION = "descripcion";
            final String OWM_SPECIALTY = "especialidad";
            final String OWM_PHONE1 = "telefono1";
            final String OWM_PHONE2 = "telefono2";
            final String OWM_EMAIL = "email";
            final String OWM_ADDRESS = "direccion";
            final String OWM_CIY = "ciudad";

            JSONObject directoryJson = new JSONObject(directoryJsonStr);
            JSONArray doctorsArray = directoryJson.getJSONArray(OWM_LIST);

            String[] resultStrs = new String[doctorsArray.length()];
            for(int i = 0; i < doctorsArray.length(); i++) {
                String name;
                String specialty;
                String city;

                // Get the JSON object representing the day
                JSONObject doctorDirectory = doctorsArray.getJSONObject(i);

                // Doctor's info is in a child object called "doctor" Try not to name variables
                // "doctor" when working with data. It confuses everybody.
                JSONObject doctorObject = doctorDirectory.getJSONObject(OWM_DOCTOR);

                name = doctorObject.getString(OWM_SURNAME) + ", " + doctorObject.getString(OWM_NAME);
                specialty  = doctorObject.getString(OWM_SPECIALTY);
                city = doctorObject.getString(OWM_CIY);

                resultStrs[i] = name + " - " + specialty + " - " + city;
            }

            for (String s: resultStrs) {
                Log.v(LOG_TAG, "Directory entry: " + s);
            }

            return resultStrs;
        }

    }


}
