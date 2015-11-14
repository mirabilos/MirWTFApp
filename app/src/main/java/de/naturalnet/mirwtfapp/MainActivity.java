/*-
 * Copyright © 2015
 *      Dominik George <nik@naturalnet.de>
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 */

package de.naturalnet.mirwtfapp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Main activity of the WTF app
 *
 * @author Dominik George <nik@naturalnet.de>
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    /**
     * Asynchronous task used to download acronyms.db from MirBSD.org
     */
    public class WTFDownloadTask extends AsyncTask<String, Integer, String> {
        private Context context;
        private PowerManager.WakeLock mWakeLock;

        /**
         * Constructor for download task
         *
         * @param context gets passed the activity that controls the task, which will always be the parent activity of this class
         */
        public WTFDownloadTask(Context context) {
            this.context = context;
        }

        /**
         * Main method triggered when the async task is run
         *
         * @param whatever Optional string to be passed, not used right now, but itnerface needs it
         * @return Status text after finishing
         */
        @Override
        protected String doInBackground(String... whatever) {
            // Variables for HTTP stream
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;

            try {
                // Set up connection via HTTP to known acronyms URL
                URL url = new URL("https://www.mirbsd.org/acronyms");
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // Return error and stop if not status 200
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Download error: HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // Reported content length
                int fileLength = connection.getContentLength();

                // Set up streams to read and write
                input = connection.getInputStream();
                output = new FileOutputStream("/sdcard/acronyms.db");

                // Set up byte array and counters for progress notification
                byte data[] = new byte[4096];
                long total = 0;
                int count;
                boolean has_tab = false;
                int count_acronyms = 0;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // Set progress in dialog
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                    for (int i = 0; i < data.length; i++) {
                        switch (data[i]) {
                        case '\t':
                            has_tab = true;
                            break;
                        case '\n':
                            if (has_tab)
                                count_acronyms++;
                            has_tab = false;
                            break;
                        }
                    }
                }

                // Store acronyms count
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("acronyms_count", count_acronyms);
                editor.commit();
            } catch (Exception e) {
                // Return any exception to the user
                return e.toString();
            } finally {
                // Clean up streams
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                    // Closing is not critical
                }

                // Close connection in case server used keep-alive
                if (connection != null)
                    connection.disconnect();
            }

            // Message success
            return "Successfully downloaded acronyms.db";
        }

        /**
         * Run before execution to set up wake lock so download is not interrupted by system sleep
         */
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
            mProgressDialog.show();
        }

        /**
         * Run on publishProgress() to update the progress dialog
         *
         * @param progress percentage of downloaded data
         */
        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(progress[0]);
        }

        /**
         * Run after execution, removes wake lock and closes progress dialog
         *
         * @param result Result text for toast
         */
        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();
            mProgressDialog.dismiss();
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
        }
    }

    // Progress dialog used by download task
    ProgressDialog mProgressDialog;

    // Instance of download task inner class
    private WTFDownloadTask wtfDownloadTask;

    // UI elements
    private Button bSearch;
    private ListView lResults;
    private TextView tHeading;
    private EditText eAcronym;
    private TextView tCats;

    // Result list stuff
    ArrayList<String> results;
    ArrayAdapter<String> resultsAdapter;

    // Storage for primitive data
    SharedPreferences prefs;

    // Counter for eastercat
    private int cats = 0;

    /**
     * Run when activity is called
     *
     * @param savedInstanceState Passed by Android if activity was run before but task killed
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load layout and fill action bar
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initalise progress dialog
        mProgressDialog = new ProgressDialog(MainActivity.this);
        mProgressDialog.setMessage("Downloading acronyms…");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);

        // Activate search button
        bSearch = (Button) findViewById(R.id.bSearch);
        bSearch.setOnClickListener(this);

        // Find text UI elements
        lResults = (ListView) findViewById(R.id.lResults);
        tHeading = (TextView) findViewById(R.id.tHeading);
        eAcronym = (EditText) findViewById(R.id.eAcronym);
        tCats = (TextView) findViewById(R.id.tCats);

        // Focus text field on start
        eAcronym.requestFocus();

        // Initalise results list
        results = new ArrayList<String>();
        resultsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results);
        lResults.setAdapter(resultsAdapter);

        // Set up preferences store
        prefs = this.getPreferences(MODE_PRIVATE);

        // Set up listener for Enter key in text field
        TextView.OnEditorActionListener acronymEnterListener = new TextView.OnEditorActionListener(){
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    try {
                        // Do acronyms search
                        doWTFSearch();
                    } catch (Exception e) {
                        // Show toast with error if acronyms.db could not be searched
                        Toast.makeText(MainActivity.this, "Error searching acronyms.db", Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            }
        };
        eAcronym.setOnEditorActionListener(acronymEnterListener);

        // Check for existence of acronyms file and downlaod if it does not exist
        File db = new File("/sdcard/acronyms.db");
        if (!db.exists()) {
            wtfDownloadTask = new WTFDownloadTask(MainActivity.this);
            wtfDownloadTask.execute();
        }
    }

    /**
     * Called to fill action bar
     *
     * @param menu The menu that got instantiated by Android
     * @return always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Called when an action bar item was clicked
     *
     * @param item instance of the clicked item
     * @return return value of superclass method
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Get id of activated menu item
        int id = item.getItemId();

        // Find out what menu entry was clicked
        if (id == R.id.action_update) {
            // "Update" item - run downlaod task
            wtfDownloadTask = new WTFDownloadTask(MainActivity.this);
            wtfDownloadTask.execute();
        } else if (id == R.id.action_about) {
            // Show about dialog
            View messageView = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.mipmap.ic_launcher);
            builder.setTitle(R.string.app_name);
            builder.setView(messageView);
            // Set acronyms info in about dialog
            TextView tAcronyms = (TextView) messageView.findViewById(R.id.tAcronyms);
            tAcronyms.setText("WTF knows about " + prefs.getInt("acronyms_count", 0) + " acronyms.");
            if (cats >= 3) {
                TextView tAcronymsSource = (TextView) messageView.findViewById(R.id.tAcronymsSource);
                tAcronymsSource.append("\n\nCat content © 2015 Dominik George, CC-BY-SA 3.0+.");
            }
            builder.create();
            builder.show();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Method called to do an actual search on acronyms
     *
     * @throws IOException if acronyms.db could not be read
     */
    private void doWTFSearch() throws IOException {
        // Normalise input
        String acronym = eAcronym.getText().toString().toUpperCase();
        if (acronym.matches(".*[A-Z]\\..*")) {
            acronym = acronym.replace(".", "");
        }

        if ((acronym.equals("MIAU") || acronym.equals("MEOW")) && (cats < 3)) {
            // Increase cat counter if MIAU
            cats++;
            tCats.append("\uD83D\uDE38");

            // Cat content if enough MIAU
            if (cats == 3) {
                View catView = getLayoutInflater().inflate(R.layout.dialog_eastercat, null, false);
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.miau);
                builder.setView(catView);
                builder.create();
                builder.show();
            }
        }

        // Empty result textbox
        resultsAdapter.clear();

        // Open acronyms.db and initialise reader
        File db = new File("/sdcard/acronyms.db");
        BufferedReader r = new BufferedReader(new FileReader(db));

        // Read fiel line by line
        String line;
        Boolean found = false;
        while ((line = r.readLine()) != null) {
            // Compare beginning of line with entered acronym followed by tab
            if (line.startsWith(acronym + "\t")) {
                // Append rest of line to result textbox if found
                resultsAdapter.add(line.substring(acronym.length() + 1));
                found = true;
            }
        }

        // show telling message if nothing was found
        if (! found) {
            resultsAdapter.add("Gee, I don’t know…");
        }

        // Set headline
        tHeading.setText(acronym + " means…");

        // Remove focus from text field and empty it
        eAcronym.clearFocus();
        eAcronym.setText("");

        // hide virtual keyboard
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(eAcronym.getWindowToken(), 0);
    }

    /**
     * Called when an UI element was clicked
     *
     * @param v calling view
     */
    @Override
    public void onClick(View v) {
        // Find out what UI element was clicked
        if (v.getId() == bSearch.getId()) {
            try {
                // Do acronyms search
                doWTFSearch();
            } catch (Exception e) {
                // Show toast with error if acronyms.db could not be searched
                Toast.makeText(this, "Error searching acronyms.db", Toast.LENGTH_LONG).show();
            }
        }
    }
}
